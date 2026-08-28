import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/**
 * Two looping layers: generated navy water, then scenery (ships/land) on top with the
 * water channel punched to alpha 0 by a flood from the middle of the image.
 */
public final class MakeLoopAssets {
  static final int WATER_DIST2 = 34 * 34;

  public static void main(String[] args) throws Exception {
    File assets = new File(args[0]);
    File outDir = new File(args[1]);
    outDir.mkdirs();
    BufferedImage scene = ImageIO.read(new File(assets, "scenery_coast.png"));
    int w = scene.getWidth();
    int h = scene.getHeight();
    int[] waterRgb = waterMedian(scene);
    boolean[] waterMask = floodWater(scene, waterRgb);
    keepOnlySolidLand(waterMask, scene, waterRgb, w, h);
    smoothContour(waterMask, w, h);
    float[] cx = new float[2];
    float[] cy = new float[2];
    landCentroids(waterMask, w, h, cx, cy);
    BufferedImage land = paintLand(scene, waterMask, w, h, cx, cy);
    ImageIO.write(land, "png", new File(outDir, "stage_land.png"));
    key(new File(assets, "spr_power.png"), new File(outDir, "spr_power.png"));
    System.out.println("land " + w + "x" + h);
  }

  private static int water(int x, int y, int w, int h) {
    double nx = 2 * Math.PI * x / w;
    double ny = 2 * Math.PI * y / h;
    double n =
        0.5
            + 0.08 * Math.sin(nx * 6)
            + 0.08 * Math.sin(ny * 8)
            + 0.05 * Math.sin(nx * 4 + ny * 5);
    int r = clamp(10 + (int) (16 * n));
    int g = clamp(24 + (int) (18 * n));
    int b = clamp(56 + (int) (30 * n));
    return (r << 16) | (g << 8) | b;
  }

  private static int[] waterMedian(BufferedImage scene) {
    int w = scene.getWidth();
    int h = scene.getHeight();
    int x0 = w * 2 / 5;
    int x1 = w * 3 / 5;
    int n = (x1 - x0) * h;
    int[] rs = new int[n];
    int[] gs = new int[n];
    int[] bs = new int[n];
    int i = 0;
    for (int y = 0; y < h; y++) {
      for (int x = x0; x < x1; x++) {
        int p = scene.getRGB(x, y);
        rs[i] = (p >> 16) & 255;
        gs[i] = (p >> 8) & 255;
        bs[i] = p & 255;
        i++;
      }
    }
    java.util.Arrays.sort(rs);
    java.util.Arrays.sort(gs);
    java.util.Arrays.sort(bs);
    int m = n / 2;
    return new int[] {rs[m], gs[m], bs[m]};
  }

  private static boolean[] floodWater(BufferedImage scene, int[] med) {
    int w = scene.getWidth();
    int h = scene.getHeight();
    boolean[] seen = new boolean[w * h];
    ArrayDeque<Integer> q = new ArrayDeque<>();
    int x0 = w * 2 / 5;
    int x1 = w * 3 / 5;
    for (int y = 0; y < h; y++) {
      for (int x = x0; x < x1; x++) {
        int i = y * w + x;
        if (dist2(scene.getRGB(x, y), med) <= WATER_DIST2) {
          seen[i] = true;
          q.add(i);
        }
      }
    }
    int[] dx = {-1, 1, 0, 0};
    int[] dy = {0, 0, -1, 1};
    while (!q.isEmpty()) {
      int i = q.removeFirst();
      int x = i % w;
      int y = i / w;
      for (int k = 0; k < 4; k++) {
        int xx = x + dx[k];
        int yy = y + dy[k];
        if (xx < 0 || xx >= w) continue;
        if (yy < 0) yy += h;
        if (yy >= h) yy -= h;
        int j = yy * w + xx;
        if (seen[j]) continue;
        if (dist2(scene.getRGB(xx, yy), med) > WATER_DIST2) continue;
        seen[j] = true;
        q.add(j);
      }
    }
    return seen;
  }

  /**
   * Land must be solid continents and ships. Specks and leftover sea paint in the
   * channel are not land — they go transparent so the water layer shows through.
   */
  private static void keepOnlySolidLand(boolean[] water, BufferedImage scene, int[] med, int w, int h) {
    boolean[] seen = new boolean[w * h];
    int[] stack = new int[w * h];
    int[] blob = new int[w * h];
    for (int start = 0; start < w * h; start++) {
      if (water[start] || seen[start]) continue;
      int top = 0;
      stack[top++] = start;
      seen[start] = true;
      int n = 0;
      int onRim = 0;
      long sr = 0, sg = 0, sb = 0;
      while (top > 0) {
        int i = stack[--top];
        blob[n++] = i;
        int x = i % w;
        int y = i / w;
        if (x == 0 || x == w - 1) onRim++;
        int p = scene.getRGB(x, y);
        sr += (p >> 16) & 255;
        sg += (p >> 8) & 255;
        sb += p & 255;
        int[] dx = {-1, 1, 0, 0};
        int[] dy = {0, 0, -1, 1};
        for (int k = 0; k < 4; k++) {
          int xx = x + dx[k];
          int yy = y + dy[k];
          if (xx < 0 || xx >= w || yy < 0 || yy >= h) continue;
          int j = yy * w + xx;
          if (water[j] || seen[j]) continue;
          seen[j] = true;
          stack[top++] = j;
        }
      }
      int ar = (int) (sr / n);
      int ag = (int) (sg / n);
      int ab = (int) (sb / n);
      int max = Math.max(ar, Math.max(ag, ab));
      int min = Math.min(ar, Math.min(ag, ab));
      int chroma = max - min;
      int dr = ar - med[0], dg = ag - med[1], db = ab - med[2];
      int toWater = dr * dr + dg * dg + db * db;
      boolean ship = chroma < 32 && max > 52 && n >= 250;
      boolean shore = onRim >= 80;
      boolean keep = shore || ship || (n >= 2500 && toWater > 40 * 40);
      if (keep) continue;
      for (int k = 0; k < n; k++) water[blob[k]] = true;
    }
  }

  private static void smoothContour(boolean[] water, int w, int h) {
    float[] mass = new float[w * h];
    for (int i = 0; i < water.length; i++) mass[i] = water[i] ? 0f : 1f;
    float[] blur = boxBlur(mass, w, h, 6);
    for (int i = 0; i < water.length; i++) water[i] = blur[i] < 0.5f;
  }

  private static float[] boxBlur(float[] src, int w, int h, int radius) {
    float[] tmp = new float[w * h];
    float[] dst = new float[w * h];
    int span = radius * 2 + 1;
    for (int y = 0; y < h; y++) {
      float acc = 0;
      for (int x = -radius; x <= radius; x++) acc += src[y * w + clampi(x, 0, w - 1)];
      for (int x = 0; x < w; x++) {
        tmp[y * w + x] = acc / span;
        acc -= src[y * w + clampi(x - radius, 0, w - 1)];
        acc += src[y * w + clampi(x + radius + 1, 0, w - 1)];
      }
    }
    for (int x = 0; x < w; x++) {
      float acc = 0;
      for (int y = -radius; y <= radius; y++) acc += tmp[clampi(y, 0, h - 1) * w + x];
      for (int y = 0; y < h; y++) {
        dst[y * w + x] = acc / span;
        acc -= tmp[clampi(y - radius, 0, h - 1) * w + x];
        acc += tmp[clampi(y + radius + 1, 0, h - 1) * w + x];
      }
    }
    return dst;
  }

  private static void landCentroids(boolean[] water, int w, int h, float[] cx, float[] cy) {
    long[] sx = new long[2], sy = new long[2], n = new long[2];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (water[y * w + x]) continue;
        int s = x < w / 2 ? 0 : 1;
        sx[s] += x;
        sy[s] += y;
        n[s]++;
      }
    }
    for (int s = 0; s < 2; s++) {
      cx[s] = n[s] == 0 ? (s == 0 ? w * 0.2f : w * 0.8f) : sx[s] / (float) n[s];
      cy[s] = n[s] == 0 ? h * 0.5f : sy[s] / (float) n[s];
    }
  }

  private static boolean isShipPaint(int p) {
    int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
    int max = Math.max(r, Math.max(g, b));
    int min = Math.min(r, Math.min(g, b));
    return max - min < 28 && max > 58 && g - r < 18 && r > 48;
  }

  private static BufferedImage paintLand(
      BufferedImage scene, boolean[] water, int w, int h, float[] cx, float[] cy) {
    float zoom = 1.72f;
    BufferedImage land = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (water[y * w + x]) {
          land.setRGB(x, y, 0);
          continue;
        }
        int src = scene.getRGB(x, y);
        if (!isShipPaint(src)) {
          int side = x < w / 2 ? 0 : 1;
          float sx = cx[side] + (x - cx[side]) / zoom;
          float sy = cy[side] + (y - cy[side]) / zoom;
          int ix = Math.round(sx);
          int iy = Math.round(sy);
          if (ix >= 0 && iy >= 0 && ix < w && iy < h && !water[iy * w + ix]) {
            src = sample(scene, sx, sy);
          }
        }
        land.setRGB(x, y, 0xff000000 | (src & 0xffffff));
      }
    }
    return land;
  }

  private static int sample(BufferedImage im, float x, float y) {
    int w = im.getWidth();
    int h = im.getHeight();
    int x0 = clampi((int) Math.floor(x), 0, w - 1);
    int y0 = clampi((int) Math.floor(y), 0, h - 1);
    int x1 = clampi(x0 + 1, 0, w - 1);
    int y1 = clampi(y0 + 1, 0, h - 1);
    float fx = x - (int) Math.floor(x);
    float fy = y - (int) Math.floor(y);
    int a = im.getRGB(x0, y0);
    int b = im.getRGB(x1, y0);
    int c = im.getRGB(x0, y1);
    int d = im.getRGB(x1, y1);
    return (lerpChan(a, b, c, d, fx, fy, 16) << 16)
        | (lerpChan(a, b, c, d, fx, fy, 8) << 8)
        | lerpChan(a, b, c, d, fx, fy, 0);
  }

  private static int lerpChan(int a, int b, int c, int d, float fx, float fy, int shift) {
    float p = ((a >> shift) & 255) * (1 - fx) + ((b >> shift) & 255) * fx;
    float q = ((c >> shift) & 255) * (1 - fx) + ((d >> shift) & 255) * fx;
    return clamp((int) (p * (1 - fy) + q * fy + 0.5f));
  }

  private static int clampi(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static int dist2(int p, int[] med) {
    int r = (p >> 16) & 255;
    int g = (p >> 8) & 255;
    int b = p & 255;
    int dr = r - med[0];
    int dg = g - med[1];
    int db = b - med[2];
    return dr * dr + dg * dg + db * db;
  }

  private static void key(File srcFile, File dstFile) throws Exception {
    if (!srcFile.isFile()) return;
    BufferedImage src = ImageIO.read(srcFile);
    BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    int minX = src.getWidth(), minY = src.getHeight(), maxX = 0, maxY = 0;
    for (int y = 0; y < src.getHeight(); y++) {
      for (int x = 0; x < src.getWidth(); x++) {
        int p = src.getRGB(x, y);
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        boolean magenta = r >= 130 && b >= 130 && g <= 130 && r + b - 2 * g >= 80;
        int a = magenta ? 0 : 255;
        if (a == 255) {
          minX = Math.min(minX, x);
          minY = Math.min(minY, y);
          maxX = Math.max(maxX, x);
          maxY = Math.max(maxY, y);
        }
        argb.setRGB(x, y, (a << 24) | (p & 0x00ffffff));
      }
    }
    if (maxX < minX) {
      ImageIO.write(argb, "png", dstFile);
      return;
    }
    int pad = 4;
    minX = Math.max(0, minX - pad);
    minY = Math.max(0, minY - pad);
    maxX = Math.min(src.getWidth() - 1, maxX + pad);
    maxY = Math.min(src.getHeight() - 1, maxY + pad);
    BufferedImage crop = argb.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    BufferedImage copy = new BufferedImage(crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D gg = copy.createGraphics();
    gg.drawImage(crop, 0, 0, null);
    gg.dispose();
    ImageIO.write(copy, "png", dstFile);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
