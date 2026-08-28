import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class MakeIcon {
  public static void main(String[] args) throws Exception {
    File srcFile = new File(args.length > 0 ? args[0] : "app/src/main/res/drawable-nodpi/spr_player.png");
    File res = new File(args.length > 1 ? args[1] : "app/src/main/res");
    BufferedImage src = ImageIO.read(srcFile);
    BufferedImage plane = crop(src);
    int size = 432;
    BufferedImage fg = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = fg.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int inner = Math.round(size * 0.58f);
    float scale = Math.min(inner / (float) plane.getWidth(), inner / (float) plane.getHeight());
    int dw = Math.max(1, Math.round(plane.getWidth() * scale));
    int dh = Math.max(1, Math.round(plane.getHeight() * scale));
    g.drawImage(plane, (size - dw) / 2, (size - dh) / 2, dw, dh, null);
    g.dispose();

    File drawable = new File(res, "drawable-nodpi");
    drawable.mkdirs();
    ImageIO.write(fg, "png", new File(drawable, "ic_launcher_foreground.png"));

    BufferedImage mono = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        int a = (fg.getRGB(x, y) >>> 24) & 255;
        if (a > 24) mono.setRGB(x, y, (a << 24) | 0xFFFFFF);
      }
    }
    ImageIO.write(mono, "png", new File(drawable, "ic_launcher_monochrome.png"));

    Color sky = new Color(0x14, 0x2C, 0x58);
    int[] densities = {48, 72, 96, 144, 192};
    String[] folders = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
    for (int i = 0; i < densities.length; i++) {
      File dir = new File(res, folders[i]);
      dir.mkdirs();
      BufferedImage icon = raster(fg, densities[i], sky);
      ImageIO.write(icon, "png", new File(dir, "ic_launcher.png"));
      ImageIO.write(icon, "png", new File(dir, "ic_launcher_round.png"));
    }
    System.out.println("icon from " + srcFile.getName() + " " + plane.getWidth() + "x" + plane.getHeight());
  }

  private static BufferedImage raster(BufferedImage fg, int size, Color sky) {
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(sky);
    g.fillRect(0, 0, size, size);
    g.drawImage(fg, 0, 0, size, size, null);
    g.dispose();
    return out;
  }

  private static BufferedImage crop(BufferedImage src) {
    int w = src.getWidth();
    int h = src.getHeight();
    int minx = w;
    int miny = h;
    int maxx = -1;
    int maxy = -1;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (((src.getRGB(x, y) >>> 24) & 255) > 16) {
          if (x < minx) minx = x;
          if (y < miny) miny = y;
          if (x > maxx) maxx = x;
          if (y > maxy) maxy = y;
        }
      }
    }
    int cw = maxx - minx + 1;
    int ch = maxy - miny + 1;
    BufferedImage out = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.drawImage(src, 0, 0, cw, ch, minx, miny, maxx + 1, maxy + 1, null);
    g.dispose();
    return out;
  }
}
