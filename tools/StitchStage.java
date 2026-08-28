import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** One-off: blend three stage tiles into a vertically looping map. */
public final class StitchStage {
  public static void main(String[] args) throws Exception {
    File dir = new File(args[0]);
    BufferedImage[] src = {
      ImageIO.read(new File(dir, "stage_islands.png")),
      ImageIO.read(new File(dir, "stage_harbor.png")),
      ImageIO.read(new File(dir, "stage_shipyard.png")),
    };
    int w = src[0].getWidth();
    for (int i = 0; i < src.length; i++) {
      if (src[i].getWidth() != w) {
        int nh = Math.max(1, src[i].getHeight() * w / src[i].getWidth());
        src[i] = scale(src[i], w, nh);
      }
    }
    int oh = Math.max(64, src[0].getHeight() / 7);
    BufferedImage[] strips = new BufferedImage[src.length];
    for (int i = 0; i < src.length; i++) {
      BufferedImage a = src[i];
      BufferedImage b = src[(i + 1) % src.length];
      strips[i] =
          blend(
              crop(a, 0, a.getHeight() - oh, w, oh),
              crop(b, 0, 0, w, oh));
    }
    int totalH = 0;
    BufferedImage[] parts = new BufferedImage[src.length * 2];
    for (int i = 0; i < src.length; i++) {
      int topCut = i == 0 ? 0 : oh;
      parts[i * 2] = crop(src[i], 0, topCut, w, src[i].getHeight() - topCut - oh);
      parts[i * 2 + 1] = strips[i];
      totalH += parts[i * 2].getHeight() + parts[i * 2 + 1].getHeight();
    }
    BufferedImage canvas = new BufferedImage(w, totalH, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = canvas.createGraphics();
    int y = 0;
    for (BufferedImage p : parts) {
      g.drawImage(p, 0, y, null);
      y += p.getHeight();
    }
    g.dispose();
    BufferedImage wrap =
        blend(crop(canvas, 0, canvas.getHeight() - oh, w, oh), crop(canvas, 0, 0, w, oh));
    Graphics2D g2 = canvas.createGraphics();
    g2.drawImage(wrap, 0, 0, null);
    g2.drawImage(wrap, 0, canvas.getHeight() - oh, null);
    g2.dispose();
    File out = new File(dir, "stage_map.png");
    ImageIO.write(canvas, "png", out);
    System.out.println("wrote " + out.getAbsolutePath() + " " + w + "x" + totalH + " overlap=" + oh);
  }

  private static BufferedImage scale(BufferedImage src, int w, int h) {
    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, w, h, null);
    g.dispose();
    return dst;
  }

  private static BufferedImage crop(BufferedImage src, int x, int y, int w, int h) {
    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = dst.createGraphics();
    g.drawImage(src, 0, 0, w, h, x, y, x + w, y + h, null);
    g.dispose();
    return dst;
  }

  private static BufferedImage blend(BufferedImage from, BufferedImage to) {
    int w = from.getWidth();
    int h = from.getHeight();
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      float u = h <= 1 ? 1f : y / (float) (h - 1);
      float t = u * u * (3f - 2f * u);
      for (int x = 0; x < w; x++) {
        int a = from.getRGB(x, y);
        int b = to.getRGB(x, y);
        int r = lerp((a >> 16) & 255, (b >> 16) & 255, t);
        int g = lerp((a >> 8) & 255, (b >> 8) & 255, t);
        int bl = lerp(a & 255, b & 255, t);
        out.setRGB(x, y, (r << 16) | (g << 8) | bl);
      }
    }
    return out;
  }

  private static int lerp(int a, int b, float t) {
    return Math.round(a * (1f - t) + b * t);
  }
}
