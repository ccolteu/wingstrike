import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;

public final class MakeAudio {
  static final int SR = 22050;
  static final Random RNG = new Random(1945);

  public static void main(String[] args) throws Exception {
    File raw = new File(args.length > 0 ? args[0] : "app/src/main/res/raw");
    raw.mkdirs();
    writeWav(new File(raw, "sfx_shot.wav"), shot(), SR);
    writeWav(new File(raw, "sfx_boom.wav"), boom(1.15, false), 44100);
    writeWav(new File(raw, "sfx_boom_big.wav"), boom(1.75, true), 44100);
    writeWav(new File(raw, "bgm_stage.wav"), music(), SR);
    System.out.println("wrote " + raw.getAbsolutePath());
  }

  static float[] shot() {
    float dur = 0.085f;
    int n = (int) (SR * dur);
    float[] out = new float[n];
    for (int i = 0; i < n; i++) {
      float t = i / (float) SR;
      float a = (float) Math.exp(-t / 0.028) * (1f - t / dur);
      float f = 1480f - 920f * (t / dur);
      float sq = Math.sin(2 * Math.PI * f * t) >= 0 ? 1f : -1f;
      float click = noise() * (float) Math.exp(-t / 0.004) * 0.35f;
      if (t > 0.012f) click = 0f;
      out[i] = sq * a * 0.38f + click;
    }
    return out;
  }

  static float[] boom(double dur, boolean big) {
    int sr = 44100;
    int n = (int) (sr * dur);
    float[] out = new float[n];
    float lp1 = 0f;
    float lp2 = 0f;
    float hp = 0f;
    float brown = 0f;
    double phase = 0;
    double phase2 = 0;
    for (int i = 0; i < n; i++) {
      float t = i / (float) sr;
      float nse = noise();
      brown = (brown + nse) * 0.5f;
      float env = (float) Math.exp(-t / (big ? 0.48 : 0.28));
      float smoke = (float) Math.pow(Math.max(0.0, 1.0 - t / dur), 1.15);
      float punch = (float) Math.exp(-t / (big ? 0.09 : 0.06));
      float fc = (big ? 90f : 110f) + (big ? 1600f : 1200f) * (float) Math.exp(-t / (big ? 0.16 : 0.11));
      float a = 1f - (float) Math.exp(-2.0 * Math.PI * fc / sr);
      float mix = nse * 0.55f + brown * 0.45f;
      lp1 += a * (mix - lp1);
      lp2 += a * (lp1 - lp2);
      hp = mix - lp2;
      float crack = hp * punch * 0.22f;
      float dropHz = (float) ((big ? 92.0 : 108.0) * Math.exp(-t / (big ? 0.22 : 0.16)));
      phase += 2.0 * Math.PI * Math.max(48.0, dropHz) / sr;
      phase2 += 2.0 * Math.PI * Math.max(36.0, dropHz * 0.5) / sr;
      float kick = (float) (Math.sin(phase) * punch * (big ? 0.95 : 0.78));
      float sub = (float) (Math.sin(phase2) * env * (big ? 0.7 : 0.52));
      float body = lp2 * (env * 0.95f + smoke * 0.55f);
      float v = body + kick + sub + crack;
      out[i] = (float) Math.tanh(v * (big ? 1.65 : 1.4));
    }
    return peak(out, 0.94f);
  }

  static float[] music() {
    double bpm = 148;
    double beat = 60.0 / bpm;
    int bars = 8;
    int n = (int) (SR * bars * 4 * beat);
    float[] out = new float[n];
    int[] bassPat = {33, 33, 33, 33, 36, 36, 31, 31, 33, 33, 38, 38, 36, 31, 33, 33};
    int[] arpA = {57, 60, 64, 69, 64, 60, 57, 52};
    int[] arpC = {60, 64, 67, 72, 67, 64, 60, 55};
    int[] lead = {
      69, 0, 72, 0, 76, 76, 74, 0, 72, 0, 69, 71, 72, 0, 64, 0,
      67, 0, 69, 71, 72, 74, 76, 0, 74, 72, 71, 0, 69, 0, 64, 67,
      69, 0, 72, 0, 76, 79, 76, 0, 74, 0, 72, 71, 69, 0, 64, 0,
      67, 69, 71, 72, 74, 0, 72, 0, 69, 0, 64, 67, 69, 0, 57, 0
    };
    for (int i = 0; i < n; i++) {
      double t = i / (double) SR;
      double beatI = t / beat;
      int bar = ((int) (beatI / 4)) % bars;
      int eighth = ((int) (beatI * 2)) % bassPat.length;
      int sixteenth = (int) (beatI * 4);
      double posInBeat = beatI % 1.0;
      float v = 0f;
      if (((int) beatI) % 2 == 0 && posInBeat < 0.12) {
        v +=
            (float)
                (Math.sin(2 * Math.PI * (90 - 50 * posInBeat / 0.12) * t)
                    * (1 - posInBeat / 0.12)
                    * 0.42);
      }
      float hat = (sixteenth % 2 == 1) ? 1f : 0.35f;
      if ((t / (beat / 2.0)) % 1.0 < 0.06) v += noise() * hat * 0.07f;
      int midi = bassPat[eighth];
      v += sq(t * hz(midi), 0.42f) * 0.16f;
      v += (float) Math.sin(2 * Math.PI * hz(midi - 12) * t) * 0.12f;
      int[] arp = bar % 2 == 0 ? arpA : arpC;
      v += sq(t * hz(arp[sixteenth % 8]), 0.25f) * 0.07f;
      int lm = lead[((int) (beatI * 2)) % lead.length];
      if (lm != 0) {
        float gate = ((t / (beat / 2.0)) % 1.0) < 0.82 ? 1f : 0f;
        v += tri(t * hz(lm)) * 0.13f * gate;
        v += sq(t * hz(lm + 12), 0.18f) * 0.03f * gate;
      }
      out[i] = v;
    }
    int delay = (int) (0.018 * SR);
    float[] wet = out.clone();
    for (int i = delay; i < n; i++) wet[i] += out[i - delay] * 0.14f;
    return peak(wet, 0.78f);
  }

  static float hz(int midi) {
    return (float) (440.0 * Math.pow(2, (midi - 69) / 12.0));
  }

  static float sq(double phase, float width) {
    double p = phase - Math.floor(phase);
    return p < width ? 1f : -1f;
  }

  static float tri(double phase) {
    double p = phase - Math.floor(phase);
    return (float) (p < 0.5 ? 4 * p - 1 : 3 - 4 * p);
  }

  static float noise() {
    return RNG.nextFloat() * 2f - 1f;
  }

  static float[] peak(float[] in, float gain) {
    float m = 0.0001f;
    for (float s : in) m = Math.max(m, Math.abs(s));
    float[] out = new float[in.length];
    for (int i = 0; i < in.length; i++) out[i] = in[i] / m * gain;
    return out;
  }

  static void writeWav(File file, float[] samples, int rate) throws Exception {
    ByteArrayOutputStream data = new ByteArrayOutputStream();
    for (float s : samples) {
      float c = Math.max(-1f, Math.min(1f, s));
      short v = (short) (c * 32767);
      data.write(v & 0xff);
      data.write((v >> 8) & 0xff);
    }
    byte[] pcm = data.toByteArray();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bos);
    out.writeBytes("RIFF");
    writeLE(out, 36 + pcm.length);
    out.writeBytes("WAVE");
    out.writeBytes("fmt ");
    writeLE(out, 16);
    writeLE16(out, 1);
    writeLE16(out, 1);
    writeLE(out, rate);
    writeLE(out, rate * 2);
    writeLE16(out, 2);
    writeLE16(out, 16);
    out.writeBytes("data");
    writeLE(out, pcm.length);
    out.write(pcm);
    out.flush();
    Files.write(file.toPath(), bos.toByteArray());
  }

  static void writeLE(DataOutputStream out, int v) throws Exception {
    out.write(v & 0xff);
    out.write((v >> 8) & 0xff);
    out.write((v >> 16) & 0xff);
    out.write((v >> 24) & 0xff);
  }

  static void writeLE16(DataOutputStream out, int v) throws Exception {
    out.write(v & 0xff);
    out.write((v >> 8) & 0xff);
  }
}
