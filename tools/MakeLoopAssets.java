import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import javax.imageio.ImageIO;

/**
 * Keys the looping land overlay (magenta = water) and writes occupancy.
 * Does not write the water layer.
 */
public final class MakeLoopAssets {
  static final int WATER_DIST2 = 34 * 34;

  public static void main(String[] args) throws Exception {
    File assets = new File(args[0]);
    File outDir = new File(args[1]);
    outDir.mkdirs();
    File masterFile = new File(assets, "stage_land_master.png");
    if (!masterFile.isFile()) masterFile = new File(assets, "stage_land.png");
    BufferedImage land;
    boolean[] waterMask;
    land = buildShoreLoop(assets);
    if (land != null) {
      waterMask = waterFromAlpha(land);
      ImageIO.write(land, "png", new File(outDir, "stage_land.png"));
      System.out.println("land " + land.getWidth() + "x" + land.getHeight() + " shore tiles");
    } else if (masterFile.isFile()) {
      land = buildLongFromMaster(masterFile);
      waterMask = waterFromAlpha(land);
      ImageIO.write(land, "png", new File(outDir, "stage_land.png"));
      System.out.println("land " + land.getWidth() + "x" + land.getHeight() + " one strip");
    } else {
      int w = 1024;
      int h = 1536;
      waterMask = geometricWater(w, h);
      land = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      ImageIO.write(land, "png", new File(outDir, "stage_land.png"));
      System.out.println("land " + w + "x" + h + " transparent");
    }
    writeLandMask(waterMask, land.getWidth(), land.getHeight(), new File(args.length > 2 ? args[2] : "LandMaskData.kt"));
  }

  /**
   * One painting: identical landing-strip pixels at both ends (circular wrap),
   * interior of that same painting repeated so the stage lasts past the boss.
   */
  private static BufferedImage buildLongFromMaster(File masterFile) throws Exception {
    File tmp = File.createTempFile("land-key", ".png");
    key(masterFile, tmp, true, 0);
    BufferedImage keyed = ImageIO.read(tmp);
    tmp.delete();
    BufferedImage master = cover(keyed, 1024, 1536);
    int capH = 256;
    int w = master.getWidth();
    int mh = master.getHeight();
    for (int y = 0; y < capH; y++) {
      for (int x = 0; x < w; x++) {
        master.setRGB(x, mh - capH + y, master.getRGB(x, y));
      }
    }
    int bodyY0 = capH;
    int bodyH = mh - capH * 2;
    int panel = 1536;
    int panels = 8;
    int longH = panel * panels;
    BufferedImage out = new BufferedImage(w, longH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.drawImage(master, 0, 0, w, capH, 0, 0, w, capH, null);
    int y = capH;
    while (y < longH - capH) {
      int chunk = Math.min(bodyH, longH - capH - y);
      g.drawImage(master, 0, y, w, y + chunk, 0, bodyY0, w, bodyY0 + chunk, null);
      y += chunk;
    }
    g.drawImage(master, 0, longH - capH, w, longH, 0, 0, w, capH, null);
    g.dispose();
    return out;
  }

  /**
   * Bake yard-scale shore tiles onto the long overlay. Middle ~2/3 stays water so
   * ships fit; left/right are packed low-altitude bases, not mirrored.
   */
  private static BufferedImage buildShoreLoop(File assets) throws Exception {
    File yard = findAsset(assets, "spr_yard.png");
    if (yard == null) return null;
    int w = 1024;
    int panel = 1536;
    int panels = 8;
    int capH = 768;
    int longH = panel * panels;
    int shoreW = w / 4;
    int maxLand = shoreW;
    BufferedImage out = new BufferedImage(w, longH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D     g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    String[] leftNames = {
      "quay4_berth.png",
      "quay4_pipes.png",
      "quay4_shed.png",
      "quay4_crates.png",
      "quay4_crane.png",
    };
    String[] rightNames = {
      "quay4_crates.png",
      "quay4_crane.png",
      "quay4_shed.png",
      "quay4_pipes.png",
      "quay4_berth.png",
    };
    int[] yardMean = yardMeanRgb(findAsset(assets, "spr_yard.png"));
    BufferedImage[] left = loadTiles(assets, leftNames, true, shoreW, yardMean);
    BufferedImage[] right = loadTiles(assets, rightNames, false, shoreW, yardMean);
    if (left.length == 0 || right.length == 0) {
      g.dispose();
      return null;
    }
    g.dispose();
    stampColumn(out, left, 0, capH, longH - capH, true);
    stampColumn(out, right, 48, capH, longH - capH, false);
    g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    BufferedImage cap = buildRunwayCap(assets, w, capH);
    if (cap != null) {
      g.drawImage(cap, 0, 0, null);
    }
    g.dispose();
    for (int y = 0; y < capH; y++) {
      for (int x = 0; x < w; x++) {
        out.setRGB(x, longH - capH + y, out.getRGB(x, y));
      }
    }
    clipToChannel(out, maxLand, capH);
    return out;
  }

  private static BufferedImage[] loadTiles(File assets, String[] names, boolean leftShore, int shoreW, int[] yardMean)
      throws Exception {
    java.util.ArrayList<BufferedImage> list = new java.util.ArrayList<>();
    for (String name : names) {
      File f = findAsset(assets, name);
      if (f == null) continue;
      BufferedImage img = keyImage(ImageIO.read(f), false);
      eatPinkFringe(img);
      img = matchTone(img, yardMean);
      if (img.getWidth() < 8 || img.getHeight() < 8) continue;
      if (leftShore) img = flipH(img);
      int dw = shoreW;
      int dh = Math.max(1, dw * img.getHeight() / img.getWidth());
      list.add(scaleTo(img, dw, dh));
      System.out.println("tile " + name + " -> " + dw + "x" + dh + (leftShore ? " L" : " R"));
    }
    return list.toArray(new BufferedImage[0]);
  }

  private static int[] yardMeanRgb(File yard) throws Exception {
    if (yard == null) return new int[] {139, 125, 98};
    BufferedImage img = keyImage(ImageIO.read(yard), false);
    return meanRgb(img);
  }

  private static int[] meanRgb(BufferedImage img) {
    long r = 0, g = 0, b = 0, n = 0;
    for (int y = 0; y < img.getHeight(); y += 2) {
      for (int x = 0; x < img.getWidth(); x += 2) {
        int p = img.getRGB(x, y);
        if (((p >>> 24) & 255) < 16) continue;
        r += (p >> 16) & 255;
        g += (p >> 8) & 255;
        b += p & 255;
        n++;
      }
    }
    if (n == 0) return new int[] {139, 125, 98};
    return new int[] {(int) (r / n), (int) (g / n), (int) (b / n)};
  }

  private static BufferedImage matchTone(BufferedImage img, int[] target) {
    if (target == null) return img;
    int[] m = meanRgb(img);
    int dr = target[0] - m[0];
    int dg = target[1] - m[1];
    int db = target[2] - m[2];
    float k = 0.45f;
    for (int y = 0; y < img.getHeight(); y++) {
      for (int x = 0; x < img.getWidth(); x++) {
        int p = img.getRGB(x, y);
        int a = (p >>> 24) & 255;
        if (a < 16) continue;
        int r = clamp(((p >> 16) & 255) + Math.round(dr * k));
        int g = clamp(((p >> 8) & 255) + Math.round(dg * k));
        int b = clamp((p & 255) + Math.round(db * k));
        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
      }
    }
    return img;
  }

  private static void stampColumn(
      BufferedImage out, BufferedImage[] tiles, int yShift, int y0, int y1, boolean left) {
    int ov = 36;
    int canvasW = out.getWidth();
    int y = y0 + (yShift % Math.max(1, tiles[0].getHeight()));
    int i = 0;
    while (y < y1) {
      BufferedImage t = tiles[i++ % tiles.length];
      int x = left ? 0 : canvasW - t.getWidth();
      int maxH = Math.min(t.getHeight(), y1 - y);
      if (maxH <= 0) break;
      blitBlend(out, t, x, y, maxH, i == 1 ? 0 : ov);
      y += Math.max(8, t.getHeight() - ov);
    }
  }

  private static void blitBlend(BufferedImage dst, BufferedImage src, int x0, int y0, int maxH, int fadeIn) {
    int w = src.getWidth();
    int h = Math.min(src.getHeight(), maxH);
    for (int y = 0; y < h; y++) {
      float fade = 1f;
      if (fadeIn > 0 && y < fadeIn) fade = y / (float) fadeIn;
      for (int x = 0; x < w; x++) {
        int sp = src.getRGB(x, y);
        int sa = (sp >>> 24) & 255;
        if (sa < 8) continue;
        int dx = x0 + x;
        int dy = y0 + y;
        if (dx < 0 || dy < 0 || dx >= dst.getWidth() || dy >= dst.getHeight()) continue;
        float a = (sa / 255f) * fade;
        int dp = dst.getRGB(dx, dy);
        int da = (dp >>> 24) & 255;
        if (da < 8 || a > 0.97f) {
          int na = Math.min(255, Math.round(sa * fade));
          dst.setRGB(dx, dy, (na << 24) | (sp & 0x00ffffff));
          continue;
        }
        float b = 1f - a;
        int r = Math.round(((sp >> 16) & 255) * a + ((dp >> 16) & 255) * b);
        int g = Math.round(((sp >> 8) & 255) * a + ((dp >> 8) & 255) * b);
        int bl = Math.round((sp & 255) * a + (dp & 255) * b);
        int aa = Math.min(255, Math.round(sa * fade + da * b));
        dst.setRGB(dx, dy, (aa << 24) | (r << 16) | (g << 8) | bl);
      }
    }
  }

  private static BufferedImage buildRunwayCap(File assets, int w, int capH) throws Exception {
    File runway = findAsset(assets, "spr_runway.png");
    if (runway == null) return null;
    BufferedImage src = keyImage(ImageIO.read(runway), false);
    int srcW = src.getWidth();
    int srcH = src.getHeight();
    int footSrc = Math.max(8, Math.round(srcH * 0.22f));
    int mid0 = Math.round(srcH * 0.16f);
    int mid1 = srcH - footSrc;
    int midH = Math.max(8, mid1 - mid0);
    int tileH = Math.max(1, (w * midH / srcW));
    while (tileH > 1 && capH % tileH != 0) tileH--;
    BufferedImage mid = scaleTo(src.getSubimage(0, mid0, srcW, midH), w, tileH);
    BufferedImage foot = scaleTo(src.getSubimage(0, mid1, srcW, footSrc), w, Math.max(48, w * footSrc / srcW));
    BufferedImage cap = new BufferedImage(w, capH, BufferedImage.TYPE_INT_ARGB);
    Graphics2D cg = cap.createGraphics();
    cg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    for (int y = 0; y < capH; y += mid.getHeight()) {
      int h = Math.min(mid.getHeight(), capH - y);
      cg.drawImage(mid, 0, y, w, y + h, 0, 0, w, h, null);
    }
    int footY = (capH - foot.getHeight()) / 2;
    cg.drawImage(foot, 0, footY, null);
    cg.dispose();
    int blend = Math.min(28, capH / 6);
    for (int y = 0; y < blend; y++) {
      float t = blend <= 1 ? 1f : y / (float) (blend - 1);
      int yBot = capH - 1 - y;
      for (int x = 0; x < w; x++) {
        int top = cap.getRGB(x, y);
        int bot = cap.getRGB(x, yBot);
        cap.setRGB(x, yBot, lerpArgb(bot, top, 1f - t));
      }
    }
    return cap;
  }

  private static int lerpArgb(int a, int b, float t) {
    t = Math.max(0f, Math.min(1f, t));
    int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
    int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
    int oA = Math.round(aa + (ba - aa) * t);
    int oR = Math.round(ar + (br - ar) * t);
    int oG = Math.round(ag + (bg - ag) * t);
    int oB = Math.round(ab + (bb - ab) * t);
    return (oA << 24) | (oR << 16) | (oG << 8) | oB;
  }

  private static void clipToChannel(BufferedImage land, int maxLand, int capH) {
    int w = land.getWidth();
    int h = land.getHeight();
    for (int y = capH; y < h - capH; y++) {
      for (int x = maxLand; x < w - maxLand; x++) land.setRGB(x, y, 0);
    }
  }

  private static File findAsset(File assets, String name) {
    File[] roots = {
      assets,
      new File(assets, "drawable-nodpi"),
      new File("app/src/main/res/drawable-nodpi"),
      new File("tools/shores"),
      new File(System.getProperty("user.dir"), "app/src/main/res/drawable-nodpi"),
      new File(System.getProperty("user.dir"), "tools/shores"),
    };
    for (File r : roots) {
      File f = new File(r, name);
      if (f.isFile()) return f;
    }
    return null;
  }

  private static BufferedImage keyImage(BufferedImage src, boolean keyNearBlack) {
    BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < src.getHeight(); y++) {
      for (int x = 0; x < src.getWidth(); x++) {
        int p = src.getRGB(x, y);
        int a = (p >>> 24) & 255;
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        boolean magenta =
            (r >= 90 && b >= 90 && g <= 160 && Math.min(r, b) - g >= 22)
                || (r >= 130 && b >= 130 && g <= 130 && r + b - 2 * g >= 80);
        boolean hole = a < 16 || magenta || (keyNearBlack && r + g + b < 28);
        argb.setRGB(x, y, hole ? 0 : ((255 << 24) | (p & 0x00ffffff)));
      }
    }
    return argb;
  }

  private static void eatPinkFringe(BufferedImage img) {
    int w = img.getWidth();
    int h = img.getHeight();
    boolean[] kill = new boolean[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int p = img.getRGB(x, y);
        int a = (p >>> 24) & 255;
        if (a < 16) continue;
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        boolean pink = r >= 80 && b >= 80 && g <= 170 && Math.min(r, b) - g >= 12;
        if (!pink) continue;
        boolean water = false;
        if (x > 0 && ((img.getRGB(x - 1, y) >>> 24) & 255) < 16) water = true;
        if (x + 1 < w && ((img.getRGB(x + 1, y) >>> 24) & 255) < 16) water = true;
        if (y > 0 && ((img.getRGB(x, y - 1) >>> 24) & 255) < 16) water = true;
        if (y + 1 < h && ((img.getRGB(x, y + 1) >>> 24) & 255) < 16) water = true;
        if (water) kill[y * w + x] = true;
      }
    }
    for (int i = 0; i < kill.length; i++) {
      if (kill[i]) img.setRGB(i % w, i / w, 0);
    }
  }

  private static BufferedImage cropOpaque(BufferedImage src, int pad) {
    int minX = src.getWidth(), minY = src.getHeight(), maxX = 0, maxY = 0;
    for (int y = 0; y < src.getHeight(); y++) {
      for (int x = 0; x < src.getWidth(); x++) {
        if (((src.getRGB(x, y) >>> 24) & 255) < 16) continue;
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }
    if (maxX < minX) return src;
    minX = Math.max(0, minX - pad);
    minY = Math.max(0, minY - pad);
    maxX = Math.min(src.getWidth() - 1, maxX + pad);
    maxY = Math.min(src.getHeight() - 1, maxY + pad);
    BufferedImage crop = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    BufferedImage copy = new BufferedImage(crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D gg = copy.createGraphics();
    gg.drawImage(crop, 0, 0, null);
    gg.dispose();
    return copy;
  }

  private static BufferedImage flipH(BufferedImage src) {
    BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.drawImage(src, src.getWidth(), 0, 0, src.getHeight(), 0, 0, src.getWidth(), src.getHeight(), null);
    g.dispose();
    return out;
  }

  private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, w, h, null);
    g.dispose();
    return out;
  }

  private static void writePanels(BufferedImage land, int panelH, File outDir) throws Exception {
    int w = land.getWidth();
    int n = Math.max(1, land.getHeight() / panelH);
    for (int i = 0; i < n; i++) {
      int h = Math.min(panelH, land.getHeight() - i * panelH);
      BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = copy.createGraphics();
      g.drawImage(land, 0, 0, w, h, 0, i * panelH, w, i * panelH + h, null);
      g.dispose();
      File dst = new File(outDir, n == 1 ? "stage_land.png" : "stage_land_" + i + ".png");
      ImageIO.write(copy, "png", dst);
      System.out.println(dst.getName() + " " + w + "x" + h);
    }
    if (n > 1) {
      BufferedImage first = new BufferedImage(w, panelH, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = first.createGraphics();
      g.drawImage(land, 0, 0, w, panelH, 0, 0, w, panelH, null);
      g.dispose();
      ImageIO.write(first, "png", new File(outDir, "stage_land.png"));
    }
  }

  private static File stitchLandPanels(File assets, File outDir) throws Exception {
    java.util.ArrayList<File> files = new java.util.ArrayList<>();
    for (int i = 0; i < 32; i++) {
      File f = new File(assets, "stage_land_" + i + ".png");
      if (!f.isFile()) break;
      files.add(f);
    }
    if (files.isEmpty()) return null;
    int tw = 1024;
    int th = 1536;
    BufferedImage out = new BufferedImage(tw, th * files.size(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    int y = 0;
    for (File f : files) {
      BufferedImage src = ImageIO.read(f);
      g.drawImage(cover(src, tw, th), 0, y, null);
      y += th;
      System.out.println("panel " + f.getName() + " " + src.getWidth() + "x" + src.getHeight());
    }
    g.dispose();
    File tmp = new File(outDir, "_stage_land_stitch.png");
    ImageIO.write(out, "png", tmp);
    System.out.println("stitched " + tw + "x" + y);
    return tmp;
  }

  private static BufferedImage cover(BufferedImage src, int w, int h) {
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
    int dw = Math.max(1, Math.round(src.getWidth() * s));
    int dh = Math.max(1, Math.round(src.getHeight() * s));
    g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    g.dispose();
    return out;
  }

  /** Keep a clear channel; preserve takeoff/landing bands at the ends of the long strip. */
  private static void punchOpenChannel(BufferedImage land) {
    int w = land.getWidth();
    int h = land.getHeight();
    int y0 = 256;
    int y1 = h - 256;
    int x0 = (int) (w / 6.0 + w * 0.04);
    int x1 = (int) (w * 5.0 / 6.0 - w * 0.04);
    for (int y = y0; y < y1; y++) {
      for (int x = x0; x < x1; x++) land.setRGB(x, y, 0);
    }
  }

  private static boolean[] waterFromAlpha(BufferedImage land) {
    int w = land.getWidth();
    int h = land.getHeight();
    boolean[] water = new boolean[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        water[y * w + x] = ((land.getRGB(x, y) >>> 24) & 255) < 16;
      }
    }
    return water;
  }

  private static boolean[] geometricWater(int w, int h) {
    boolean[] water = new boolean[w * h];
    for (int y = 0; y < h; y++) {
      double wave = 7 * Math.sin(y * 0.014);
      int x0 = (int) (w * 0.26 + wave);
      int x1 = (int) (w * 0.74 + wave);
      for (int x = 0; x < w; x++) water[y * w + x] = x > x0 && x < x1;
    }
    return water;
  }

  private static void writeLandMask(boolean[] water, int w, int h, File out) throws Exception {
    int mw = 64;
    int mh = Math.max(96, 96 * h / 1536);
    int nLongs = (mw * mh + 63) / 64;
    long[] packed = new long[nLongs];
    for (int y = 0; y < mh; y++) {
      for (int x = 0; x < mw; x++) {
        int sx = x * w / mw;
        int sy = y * h / mh;
        if (water[sy * w + sx]) continue;
        int i = y * mw + x;
        packed[i >>> 6] |= 1L << (i & 63);
      }
    }
    out.getParentFile().mkdirs();
    java.io.PrintWriter pw = new java.io.PrintWriter(out);
    pw.println("package com.example.wingstrike.game");
    pw.println();
    pw.println("/** Packed land occupancy for the looping stage tile. Generated by MakeLoopAssets. */");
    pw.println("internal object LandMaskData {");
    pw.println("  const val W = " + mw);
    pw.println("  const val H = " + mh);
    pw.println("  val bits = longArrayOf(");
    for (int i = 0; i < packed.length; i++) {
      if (packed[i] == Long.MIN_VALUE) pw.print("    Long.MIN_VALUE");
      else pw.print("    " + packed[i] + "L");
      if (i + 1 < packed.length) pw.print(",");
      pw.println();
    }
    pw.println("  )");
    pw.println("}");
    pw.close();
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
   * Keep solid continents. Isolated specks and leftover sea paint go transparent.
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
      boolean shore = onRim >= 80;
      boolean keep = shore || (n >= 2500 && toWater > 40 * 40);
      if (keep) continue;
      for (int k = 0; k < n; k++) water[blob[k]] = true;
    }
  }

  private static void smoothContour(boolean[] water, int w, int h) {
    float[] mass = new float[w * h];
    for (int i = 0; i < water.length; i++) mass[i] = water[i] ? 0f : 1f;
    float[] blur = boxBlur(mass, w, h, 16);
    for (int i = 0; i < water.length; i++) water[i] = blur[i] < 0.58f;
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

  /** Gray hulls/boats that sit against the channel become water so sprites can occupy the bay. */
  private static void eatHarborGray(boolean[] water, BufferedImage scene, int w, int h) {
    for (int pass = 0; pass < 48; pass++) {
      boolean changed = false;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int i = y * w + x;
          if (water[i] || !grayHull(scene.getRGB(x, y))) continue;
          int toward = x < w / 2 ? 1 : -1;
          int waterAhead = 0;
          for (int k = 1; k <= 16; k++) {
            int xx = x + toward * k;
            if (xx < 0 || xx >= w) break;
            if (water[y * w + xx]) waterAhead++;
          }
          int wn = 0;
          if (x > 0 && water[i - 1]) wn++;
          if (x + 1 < w && water[i + 1]) wn++;
          if (y > 0 && water[i - w]) wn++;
          if (y + 1 < h && water[i + w]) wn++;
          if (wn > 0 && waterAhead >= 5) {
            water[i] = true;
            changed = true;
          }
        }
      }
      if (!changed) break;
    }
  }

  /** Cut docks, hulls, and runways that stick farther into the channel than the typical coast. */
  private static void clipCoastSpikes(boolean[] water, int w, int h) {
    int[] left = new int[h];
    int[] right = new int[h];
    for (int y = 0; y < h; y++) {
      int last = 0;
      for (int x = 0; x < w / 2; x++) {
        if (!water[y * w + x]) last = x;
      }
      left[y] = last;
      int first = w - 1;
      for (int x = w - 1; x >= w / 2; x--) {
        if (!water[y * w + x]) first = x;
      }
      right[y] = first;
    }
    int[] sl = median1d(left, 51);
    int[] sr = median1d(right, 51);
    for (int y = 0; y < h; y++) {
      int maxL = sl[y] + 3;
      int minR = sr[y] - 3;
      for (int x = maxL + 1; x < w / 2; x++) water[y * w + x] = true;
      for (int x = w / 2; x < minR; x++) water[y * w + x] = true;
    }
  }

  private static int[] median1d(int[] src, int window) {
    int n = src.length;
    int[] dst = new int[n];
    int[] buf = new int[window];
    int r = window / 2;
    for (int i = 0; i < n; i++) {
      for (int k = 0; k < window; k++) {
        int j = i - r + k;
        j %= n;
        if (j < 0) j += n;
        buf[k] = src[j];
      }
      java.util.Arrays.sort(buf);
      dst[i] = buf[r];
    }
    return dst;
  }

  private static void dropSpecks(boolean[] water, int w, int h) {
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
      while (top > 0) {
        int i = stack[--top];
        blob[n++] = i;
        int x = i % w;
        if (x == 0 || x == w - 1) onRim++;
        int y = i / w;
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
      if (onRim >= 80) continue;
      for (int k = 0; k < n; k++) water[blob[k]] = true;
    }
  }

  private static boolean grayHull(int p) {
    int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
    int max = Math.max(r, Math.max(g, b));
    int min = Math.min(r, Math.min(g, b));
    return max - min < 28 && max > 58 && max < 210 && Math.abs(g - r) < 18;
  }

  private static int[] shoreDist(boolean[] water, int w, int h) {
    int[] d = new int[w * h];
    for (int i = 0; i < d.length; i++) d[i] = water[i] ? 0 : 999;
    for (int pass = 0; pass < 14; pass++) {
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int i = y * w + x;
          if (d[i] == 0) continue;
          int best = d[i];
          if (x > 0) best = Math.min(best, d[i - 1] + 1);
          if (x + 1 < w) best = Math.min(best, d[i + 1] + 1);
          if (y > 0) best = Math.min(best, d[i - w] + 1);
          if (y + 1 < h) best = Math.min(best, d[i + w] + 1);
          d[i] = best;
        }
      }
    }
    return d;
  }

  private static BufferedImage paintBarren(boolean[] water, int w, int h) {
    int[] dist = shoreDist(water, w, h);
    BufferedImage land = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    int[] grassA = {52, 78, 36};
    int[] grassB = {98, 118, 48};
    int[] dirt = {118, 92, 54};
    int[] sand = {148, 128, 82};
    int[] rock = {78, 72, 60};
    int[] tree = {32, 54, 26};
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int i = y * w + x;
        if (water[i]) {
          land.setRGB(x, y, 0);
          continue;
        }
        float n = fbm(x / 42f, y / 42f, h);
        float n2 = fbm(x / 11f + 9.2f, y / 11f, h);
        float grove = fbm(x / 26f + 4f, y / 26f + 2f, h);
        float shore = 1f - Math.min(dist[i], 12) / 12f;
        int[] a = lerpRgb(grassA, dirt, clamp01(n * 0.85f + n2 * 0.15f));
        a = lerpRgb(a, grassB, clamp01(n2 * 0.45f));
        if (shore > 0.35f) {
          a = lerpRgb(a, rock, clamp01((shore - 0.35f) * 1.4f));
          a = lerpRgb(a, sand, clamp01((shore - 0.55f) * 1.6f) * 0.45f);
        }
        if (grove > 0.68f && n2 > 0.38f && shore < 0.55f) {
          a = lerpRgb(a, tree, clamp01((grove - 0.68f) * 4f));
        }
        int grain = (int) ((n2 - 0.5f) * 18);
        int rgb =
            (clamp(a[0] + grain) << 16) | (clamp(a[1] + grain) << 8) | clamp(a[2] + grain / 2);
        land.setRGB(x, y, 0xff000000 | rgb);
      }
    }
    return land;
  }

  private static int[] lerpRgb(int[] a, int[] b, float t) {
    t = clamp01(t);
    return new int[] {
      (int) (a[0] + (b[0] - a[0]) * t + 0.5f),
      (int) (a[1] + (b[1] - a[1]) * t + 0.5f),
      (int) (a[2] + (b[2] - a[2]) * t + 0.5f),
    };
  }

  private static float clamp01(float v) {
    return Math.max(0f, Math.min(1f, v));
  }

  private static float hash(int x, int y) {
    int n = x * 374761393 + y * 668265263;
    n = (n ^ (n >> 13)) * 1274126177;
    return ((n ^ (n >> 16)) & 0x7fffffff) / (float) Integer.MAX_VALUE;
  }

  private static float valueNoise(float x, float y, int yPeriod) {
    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    float fx = x - x0;
    float fy = y - y0;
    fx = fx * fx * (3 - 2 * fx);
    fy = fy * fy * (3 - 2 * fy);
    int y1 = y0 + 1;
    float a = hash(x0, wrap(y0, yPeriod));
    float b = hash(x0 + 1, wrap(y0, yPeriod));
    float c = hash(x0, wrap(y1, yPeriod));
    float d = hash(x0 + 1, wrap(y1, yPeriod));
    return a * (1 - fx) * (1 - fy) + b * fx * (1 - fy) + c * (1 - fx) * fy + d * fx * fy;
  }

  private static int wrap(int y, int period) {
    int v = y % period;
    return v < 0 ? v + period : v;
  }

  private static float fbm(float x, float y, int yPeriod) {
    float sum = 0;
    float amp = 1;
    float norm = 0;
    float ys = yPeriod / 42f;
    for (int i = 0; i < 5; i++) {
      sum += amp * valueNoise(x, y, Math.max(2, (int) Math.ceil(ys)));
      norm += amp;
      x *= 2f;
      y *= 2f;
      ys *= 2f;
      amp *= 0.5f;
    }
    return sum / norm;
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

  private static void key(File srcFile, File dstFile, boolean keepSize, int pad) throws Exception {
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
    BufferedImage copy;
    if (keepSize || maxX < minX) {
      copy = argb;
    } else {
      minX = Math.max(0, minX - pad);
      minY = Math.max(0, minY - pad);
      maxX = Math.min(src.getWidth() - 1, maxX + pad);
      maxY = Math.min(src.getHeight() - 1, maxY + pad);
      BufferedImage crop = argb.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
      copy = new BufferedImage(crop.getWidth(), crop.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D gg = copy.createGraphics();
      gg.drawImage(crop, 0, 0, null);
      gg.dispose();
    }
    if (dstFile.getName().equals("spr_patrol.png")) {
      copy = squashRatio(copy, 2, 3);
    }
    ImageIO.write(copy, "png", dstFile);
    System.out.println(dstFile.getName() + " " + copy.getWidth() + "x" + copy.getHeight());
  }

  private static boolean dockHole(int p) {
    int a = (p >>> 24) & 255;
    int r = (p >> 16) & 255;
    int g = (p >> 8) & 255;
    int b = p & 255;
    if (a < 20) return true;
    return r >= 130 && b >= 130 && g <= 130 && r + b - 2 * g >= 80;
  }

  /** Open the U-berth (black/magenta fill on the left) so water and a docked ship show through. */
  private static void punchDockBerth(File dock, double xMaxFrac) throws Exception {
    if (!dock.isFile()) return;
    BufferedImage img = ImageIO.read(dock);
    int w = img.getWidth();
    int h = img.getHeight();
    int xMax = (int) (w * xMaxFrac);
    boolean[] seen = new boolean[w * h];
    ArrayDeque<Integer> q = new ArrayDeque<>();
    int seeds = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < Math.max(8, w / 20); x++) {
        int i = y * w + x;
        if (!dockHole(img.getRGB(x, y))) continue;
        seen[i] = true;
        q.add(i);
        seeds++;
      }
    }
    int[] dx = {-1, 1, 0, 0};
    int[] dy = {0, 0, -1, 1};
    int punched = 0;
    while (!q.isEmpty()) {
      int i = q.removeFirst();
      int x = i % w;
      int y = i / w;
      img.setRGB(x, y, 0);
      punched++;
      for (int k = 0; k < 4; k++) {
        int xx = x + dx[k];
        int yy = y + dy[k];
        if (xx < 0 || yy < 0 || xx >= w || yy >= h || xx > xMax) continue;
        int j = yy * w + xx;
        if (seen[j]) continue;
        if (!dockHole(img.getRGB(xx, yy))) continue;
        seen[j] = true;
        q.add(j);
      }
    }
    ImageIO.write(img, "png", dock);
    System.out.println("dock berth punched " + w + "x" + h + " seeds=" + seeds + " px=" + punched);
  }

  private static BufferedImage squashRatio(BufferedImage src, int rw, int rh) {
    int w = Math.max(src.getWidth(), 64);
    int h = Math.max(1, w * rh / rw);
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, w, h, null);
    g.dispose();
    return out;
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
