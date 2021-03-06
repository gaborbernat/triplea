package games.strategy.triplea.image;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import games.strategy.debug.ClientLogger;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.image.BlendComposite.BlendingMode;
import games.strategy.triplea.util.Stopwatch;
import games.strategy.ui.Util;

public final class TileImageFactory {
  private final Object m_mutex = new Object();
  // one instance in the application
  private final static String SHOW_RELIEF_IMAGES_PREFERENCE = "ShowRelief2";
  private static boolean s_showReliefImages = true;
  private final static String SHOW_MAP_BLENDS_PREFERENCE = "ShowBlends";
  private static boolean s_showMapBlends = false;
  private final static String SHOW_MAP_BLEND_MODE = "BlendMode";
  private static String s_showMapBlendMode = "normal";
  private final static String SHOW_MAP_BLEND_ALPHA = "BlendAlpha";
  private static float s_showMapBlendAlpha = 1.0f;
  private final Composite composite = AlphaComposite.Src;
  private static GraphicsConfiguration configuration =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
  private static final Logger s_logger = Logger.getLogger(TileImageFactory.class.getName());
  private double m_scale = 1;
  // maps image name to ImageRef
  private HashMap<String, ImageRef> m_imageCache = new HashMap<String, ImageRef>();

  static {
    final Preferences prefs = Preferences.userNodeForPackage(TileImageFactory.class);
    s_showReliefImages = prefs.getBoolean(SHOW_RELIEF_IMAGES_PREFERENCE, true);
    s_showMapBlends = prefs.getBoolean(SHOW_MAP_BLENDS_PREFERENCE, false);
    s_showMapBlendMode = prefs.get(SHOW_MAP_BLEND_MODE, "normal");
    s_showMapBlendAlpha = prefs.getFloat(SHOW_MAP_BLEND_ALPHA, 1.0f);
  }

  public static boolean getShowReliefImages() {
    return s_showReliefImages;
  }

  public static boolean getShowMapBlends() {
    return s_showMapBlends;
  }

  private static String getShowMapBlendMode() {
    return s_showMapBlendMode.toUpperCase();
  }

  private static float getShowMapBlendAlpha() {
    return s_showMapBlendAlpha;
  }

  public void setScale(final double newScale) {
    if (newScale > 1) {
      throw new IllegalArgumentException("Wrong scale");
    }
    synchronized (m_mutex) {
      m_scale = newScale;
      getM_imageCache().clear();
    }
  }

  public static void setShowReliefImages(final boolean aBool) {
    s_showReliefImages = aBool;
    final Preferences prefs = Preferences.userNodeForPackage(TileImageFactory.class);
    prefs.putBoolean(SHOW_RELIEF_IMAGES_PREFERENCE, s_showReliefImages);
    try {
      prefs.flush();
    } catch (final BackingStoreException ex) {
      ex.printStackTrace();
    }
  }

  public static void setShowMapBlends(final boolean aBool) {
    s_showMapBlends = aBool;
    final Preferences prefs = Preferences.userNodeForPackage(TileImageFactory.class);
    prefs.putBoolean(SHOW_MAP_BLENDS_PREFERENCE, s_showMapBlends);
    try {
      prefs.flush();
    } catch (final BackingStoreException ex) {
      ex.printStackTrace();
    }
  }

  public static void setShowMapBlendMode(final String aString) {
    s_showMapBlendMode = aString;
    final Preferences prefs = Preferences.userNodeForPackage(TileImageFactory.class);
    prefs.put(SHOW_MAP_BLEND_MODE, s_showMapBlendMode);
    try {
      prefs.flush();
    } catch (final BackingStoreException ex) {
      ex.printStackTrace();
    }
  }

  public static void setShowMapBlendAlpha(final float aFloat) {
    s_showMapBlendAlpha = aFloat;
    final Preferences prefs = Preferences.userNodeForPackage(TileImageFactory.class);
    prefs.putFloat(SHOW_MAP_BLEND_ALPHA, s_showMapBlendAlpha);
    try {
      prefs.flush();
    } catch (final BackingStoreException ex) {
      ex.printStackTrace();
    }
  }

  private ResourceLoader m_resourceLoader;

  public void setMapDir(final ResourceLoader loader) {
    m_resourceLoader = loader;
    synchronized (m_mutex) {
      // we manually want to clear each ref to allow the soft reference to
      // be removed
      final Iterator<ImageRef> values = getM_imageCache().values().iterator();
      while (values.hasNext()) {
        final ImageRef imageRef = values.next();
        imageRef.clear();
      }
      getM_imageCache().clear();
    }
  }

  public TileImageFactory() {}

  /**
   * @param fileName
   */
  private Image isImageLoaded(final String fileName) {
    if (getM_imageCache().get(fileName) == null) {
      return null;
    }
    return getM_imageCache().get(fileName).getImage();
  }

  public Image getBaseTile(final int x, final int y) {
    final String fileName = getBaseTileImageName(x, y);
    if (m_resourceLoader.getResource(fileName) == null) {
      return null;
    }
    return getImage(fileName, false);
  }

  public Image getUnscaledUncachedBaseTile(final int x, final int y) {
    final String fileName = getBaseTileImageName(x, y);
    final URL url = m_resourceLoader.getResource(fileName);
    if (url == null) {
      return null;
    }
    return loadImage(url, fileName, false, false, false);
  }

  private static String getBaseTileImageName(final int x, final int y) {
    // we are loading with a class loader now, use /
    final String fileName = "baseTiles" + "/" + x + "_" + y + ".png";
    return fileName;
  }

  /**
   * @param fileName
   */
  private Image getImage(final String fileName, final boolean transparent) {
    synchronized (m_mutex) {
      final Image rVal = isImageLoaded(fileName);
      if (rVal != null) {
        return rVal;
      }
      // This is null if there is no image
      final URL url = m_resourceLoader.getResource(fileName);

      if ((!s_showMapBlends || !s_showReliefImages || !transparent) && url == null) {
        return null;
      }
      loadImage(url, fileName, transparent, true, true);
    }
    return getImage(fileName, transparent);
  }

  public Image getReliefTile(final int a, final int b) {
    final String fileName = getReliefTileImageName(a, b);
    return getImage(fileName, true);
  }

  public Image getUnscaledUncachedReliefTile(final int x, final int y) {
    final String fileName = getReliefTileImageName(x, y);
    final URL url = m_resourceLoader.getResource(fileName);
    if (url == null) {
      return null;
    }
    return loadImage(url, fileName, true, false, false);
  }

  private static String getReliefTileImageName(final int x, final int y) {
    // we are loading with a class loader now, use /
    final String fileName = "reliefTiles" + "/" + x + "_" + y + ".png";
    return fileName;
  }

  /**
   * @return compatibleImage This method produces a blank white tile for use in blending.
   */
  private static BufferedImage makeMissingBaseTile(final BufferedImage input) {
    final BufferedImage compatibleImage =
        configuration.createCompatibleImage(input.getWidth(null), input.getHeight(null), Transparency.TRANSLUCENT);
    final Graphics2D g2 = compatibleImage.createGraphics();
    g2.fillRect(0, 0, input.getWidth(null), input.getHeight(null));
    g2.drawImage(compatibleImage, 0, 0, null);
    g2.dispose();
    return compatibleImage;
  }


  private Image loadImage(final URL imageLocation, final String fileName, final boolean transparent,
      final boolean cache, final boolean scale) {
    if (s_showMapBlends && s_showReliefImages && transparent) {
      return loadBlendedImage(imageLocation, fileName, transparent, cache, scale);
    } else {
      return loadUnblendedImage(imageLocation, fileName, transparent, cache, scale);
    }
  }

  private Image loadBlendedImage(final URL imageLocation, final String fileName, final boolean transparent,
      final boolean cache, final boolean scale) {
    BufferedImage reliefFile = null;
    BufferedImage baseFile = null;
    // The relief tile
    final String reliefFileName = fileName.replace("baseTiles", "reliefTiles");
    final URL urlrelief = m_resourceLoader.getResource(reliefFileName);
    // The base tile
    final String baseFileName = fileName.replace("reliefTiles", "baseTiles");
    final URL urlBase = m_resourceLoader.getResource(baseFileName);
    // blank relief tile
    final String blankReliefFileName = "reliefTiles/blank_relief.png";
    final URL urlBlankRelief = m_resourceLoader.getResource(blankReliefFileName);

    // Get buffered images
    try {
      final Stopwatch loadingImages =
          new Stopwatch(s_logger, Level.FINE, "Loading images:" + urlrelief + " and " + urlBase);
      if (urlrelief != null) {
        reliefFile = loadCompatibleImage(urlrelief);
      }
      if (urlBase != null) {
        baseFile = loadCompatibleImage(urlBase);
      }
      loadingImages.done();
    } catch (final IOException e) {
      e.printStackTrace();
    }

    // This does the blend
    final float alpha = getShowMapBlendAlpha();
    final int overX = 0;
    final int overY = 0;
    if (reliefFile == null) {
      try {
        reliefFile = loadCompatibleImage(urlBlankRelief);
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
    // This fixes the blank land territories
    if (baseFile == null) {
      baseFile = makeMissingBaseTile(reliefFile);
    }
    /* reversing the to/from files leaves white underlays visible */
    if (reliefFile != null) {
      final Graphics2D g2 = reliefFile.createGraphics();
      if (scale && m_scale != 1.0) {
        final AffineTransform transform = new AffineTransform();
        transform.scale(m_scale, m_scale);
        g2.setTransform(transform);
      }
      g2.drawImage(reliefFile, overX, overY, null);
      // gets the blending mode from the map.properties file (sometimes)
      final BlendingMode blendMode = BlendComposite.BlendingMode.valueOf(getShowMapBlendMode());
      final BlendComposite blendComposite = BlendComposite.getInstance(blendMode).derive(alpha);
      // g2.setComposite(BlendComposite.Overlay.derive(alpha));
      g2.setComposite(blendComposite);
      g2.drawImage(baseFile, overX, overY, null);
      final ImageRef ref = new ImageRef(reliefFile);
      if (cache) {
        getM_imageCache().put(fileName, ref);
      }
      return reliefFile;
    } else {
      final ImageRef ref = new ImageRef(baseFile);
      if (cache) {
        getM_imageCache().put(fileName, ref);
      }
      return baseFile;
    }
  }

  private Image loadUnblendedImage(final URL imageLocation, final String fileName, final boolean transparent,
      final boolean cache, final boolean scale) {
    Image image;
    try {
      final Stopwatch loadingImages = new Stopwatch(s_logger, Level.FINE, "Loading image:" + imageLocation);
      final BufferedImage fromFile = ImageIO.read(imageLocation);
      loadingImages.done();
      final Stopwatch copyingImage = new Stopwatch(s_logger, Level.FINE, "Copying image:" + imageLocation);
      // if we dont copy, drawing the tile to the screen takes significantly longer
      // has something to do with the colour model and type of the images
      // some images can be copeid quickly to the screen
      // this step is a significant bottle neck in the image drawing process
      // we should try to find a way to avoid it, and load the
      // png directly as the right type
      image = Util.createImage(fromFile.getWidth(null), fromFile.getHeight(null), transparent);
      final Graphics2D g = (Graphics2D) image.getGraphics();
      if (scale && m_scale != 1.0) {
        final AffineTransform transform = new AffineTransform();
        transform.scale(m_scale, m_scale);
        g.setTransform(transform);
      }
      g.drawImage(fromFile, 0, 0, null);
      g.dispose();
      fromFile.flush();
      copyingImage.done();
    } catch (final IOException e) {
      ClientLogger.logError("Could not load image, url: "+ imageLocation.toString(), e);
      image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    }
    final ImageRef ref = new ImageRef(image);
    if (cache) {
      getM_imageCache().put(fileName, ref);
    }
    return image;
  }

  public Composite getComposite() {
    return this.composite;
  }

  private static BufferedImage loadCompatibleImage(final URL resource) throws IOException {
    final BufferedImage image = ImageIO.read(resource);
    return toCompatibleImage(image);
  }

  private static BufferedImage toCompatibleImage(final BufferedImage image) {
    final BufferedImage compatibleImage =
        configuration.createCompatibleImage(image.getWidth(), image.getHeight(), Transparency.TRANSLUCENT);
    final Graphics g = compatibleImage.getGraphics();
    g.drawImage(image, 0, 0, null);
    g.dispose();
    return compatibleImage;
  }

  public static BufferedImage createCompatibleImage(final int width, final int height) {
    return configuration.createCompatibleImage(width, height);
  }

  public void setM_imageCache(final HashMap<String, ImageRef> m_imageCache) {
    this.m_imageCache = m_imageCache;
  }

  public HashMap<String, ImageRef> getM_imageCache() {
    return m_imageCache;
  }
}


/**
 * We keep a soft reference to the image to allow it to be garbage collected.
 * Also, the image may not have finished watching when we are created, but the
 * getImage method ensures that the image will be loaded before returning.
 */
class ImageRef {
  public static final ReferenceQueue<Image> s_referenceQueue = new ReferenceQueue<Image>();
  public static final Logger s_logger = Logger.getLogger(ImageRef.class.getName());
  private static final AtomicInteger s_imageCount = new AtomicInteger();

  static {
    final Runnable r = new Runnable() {
      @Override
      public void run() {
        while (true) {
          try {
            s_referenceQueue.remove();
            s_logger.finer("Removed soft reference image. Image count:" + s_imageCount.decrementAndGet());
          } catch (final InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    };
    final Thread t = new Thread(r, "Tile Image Factory Soft Reference Reclaimer");
    t.setDaemon(true);
    t.start();
  }

  private final Reference<Image> m_image;

  // private final Object m_hardRef;
  public ImageRef(final Image image) {
    m_image = new SoftReference<Image>(image, s_referenceQueue);
    // m_hardRef = image;
    s_logger.finer("Added soft reference image. Image count:" + s_imageCount.incrementAndGet());
  }

  public Image getImage() {
    return m_image.get();
  }

  public void clear() {
    m_image.enqueue();
    m_image.clear();
  }
}


/**
 * This class handles the various types of blends for base/relief tiles
 */
class BlendComposite implements java.awt.Composite {
  public enum BlendingMode {
    NORMAL, OVERLAY, MULTIPLY, DIFFERENCE, LINEAR_LIGHT
  }

  public static final BlendComposite Normal = new BlendComposite(BlendingMode.NORMAL);
  public static final BlendComposite Overlay = new BlendComposite(BlendingMode.OVERLAY);
  public static final BlendComposite Multiply = new BlendComposite(BlendingMode.MULTIPLY);
  public static final BlendComposite Difference = new BlendComposite(BlendingMode.DIFFERENCE);
  public static final BlendComposite Linear_Light = new BlendComposite(BlendingMode.LINEAR_LIGHT);
  private float alpha;
  private final BlendingMode mode;

  BlendComposite(final BlendingMode mode) {
    this(mode, 1.0f);
  }

  private BlendComposite(final BlendingMode mode, final float alpha) {
    this.mode = mode;
    setAlpha(alpha);
  }

  public static BlendComposite getInstance(final BlendingMode mode) {
    return new BlendComposite(mode);
  }

  public static BlendComposite getInstance(final BlendingMode mode, final float alpha) {
    return new BlendComposite(mode, alpha);
  }

  public BlendComposite derive(final BlendingMode mode) {
    return this.mode == mode ? this : new BlendComposite(mode, getAlpha());
  }

  public BlendComposite derive(final float alpha) {
    return this.alpha == alpha ? this : new BlendComposite(getMode(), alpha);
  }

  public float getAlpha() {
    return alpha;
  }

  public BlendingMode getMode() {
    return mode;
  }

  private void setAlpha(final float alpha) {
    if (alpha < 0.0f || alpha > 1.0f) {
      throw new IllegalArgumentException("alpha must be comprised between 0.0f and 1.0f");
    }
    this.alpha = alpha;
  }

  @Override
  public CompositeContext createContext(final ColorModel srcColorModel, final ColorModel dstColorModel,
      final RenderingHints hints) {
    return new BlendingContext(this);
  }

  private static final class BlendingContext implements CompositeContext {
    private final Blender blender;
    private final BlendComposite composite;

    private BlendingContext(final BlendComposite composite) {
      this.composite = composite;
      this.blender = Blender.getBlenderFor(composite);
    }

    @Override
    public void dispose() {}

    @Override
    public void compose(final Raster src, final Raster dstIn, final WritableRaster dstOut) {
      if (src.getSampleModel().getDataType() != DataBuffer.TYPE_INT
          || dstIn.getSampleModel().getDataType() != DataBuffer.TYPE_INT
          || dstOut.getSampleModel().getDataType() != DataBuffer.TYPE_INT) {
        throw new IllegalStateException("Source and destination must store pixels as INT.");
      }
      final int width = Math.min(src.getWidth(), dstIn.getWidth());
      final int height = Math.min(src.getHeight(), dstIn.getHeight());
      final float alpha = composite.getAlpha();
      final int[] srcPixel = new int[4];
      final int[] dstPixel = new int[4];
      final int[] srcPixels = new int[width];
      final int[] dstPixels = new int[width];
      for (int y = 0; y < height; y++) {
        src.getDataElements(0, y, width, 1, srcPixels);
        dstIn.getDataElements(0, y, width, 1, dstPixels);
        for (int x = 0; x < width; x++) {
          // pixels are stored as INT_ARGB
          // our arrays are [R, G, B, A]
          int pixel = srcPixels[x];
          srcPixel[0] = (pixel >> 16) & 0xFF;
          srcPixel[1] = (pixel >> 8) & 0xFF;
          srcPixel[2] = (pixel) & 0xFF;
          srcPixel[3] = (pixel >> 24) & 0xFF;
          pixel = dstPixels[x];
          dstPixel[0] = (pixel >> 16) & 0xFF;
          dstPixel[1] = (pixel >> 8) & 0xFF;
          dstPixel[2] = (pixel) & 0xFF;
          dstPixel[3] = (pixel >> 24) & 0xFF;
          final int[] result = blender.blend(srcPixel, dstPixel);
          // mixes the result with the opacity
          dstPixels[x] = ((int) (dstPixel[3] + (result[3] - dstPixel[3]) * alpha) & 0xFF) << 24
              | ((int) (dstPixel[0] + (result[0] - dstPixel[0]) * alpha) & 0xFF) << 16
              | ((int) (dstPixel[1] + (result[1] - dstPixel[1]) * alpha) & 0xFF) << 8
              | (int) (dstPixel[2] + (result[2] - dstPixel[2]) * alpha) & 0xFF;
        }
        dstOut.setDataElements(0, y, width, 1, dstPixels);
      }
    }
  }
  static abstract class Blender {
    public abstract int[] blend(int[] src, int[] dst);

    private static Blender getBlenderFor(final BlendComposite composite) {
      switch (composite.getMode()) {
        case NORMAL:
          return new Blender() {
            @Override
            public int[] blend(final int[] src, final int[] dst) {
              return src;
            }
          };
        case OVERLAY:
          return new Blender() {
            @Override
            public int[] blend(final int[] src, final int[] dst) {
              return new int[] {dst[0] < 128 ? dst[0] * src[0] >> 7 : 255 - ((255 - dst[0]) * (255 - src[0]) >> 7),
                  dst[1] < 128 ? dst[1] * src[1] >> 7 : 255 - ((255 - dst[1]) * (255 - src[1]) >> 7),
                  dst[2] < 128 ? dst[2] * src[2] >> 7 : 255 - ((255 - dst[2]) * (255 - src[2]) >> 7),
                  Math.min(255, src[3] + dst[3])};
            }
          };
        case LINEAR_LIGHT:
          return new Blender() {
            @Override
            public int[] blend(final int[] src, final int[] dst) {
              return new int[] {dst[0] < 128 ? dst[0] + src[0] >> 7 - 255 : dst[0] + (src[0] - 128) >> 7,
                  dst[1] < 128 ? dst[1] + src[1] >> 7 - 255 : dst[1] + (src[1] - 128) >> 7,
                  dst[2] < 128 ? dst[2] + src[2] >> 7 - 255 : dst[2] + (src[2] - 128) >> 7,
                  Math.min(255, src[3] + dst[3])};
            }
          };
        case MULTIPLY:
          return new Blender() {
            @Override
            public int[] blend(final int[] src, final int[] dst) {
              return new int[] {(src[0] * dst[0]) >> 8, (src[1] * dst[1]) >> 8, (src[2] * dst[2]) >> 8,
                  Math.min(255, src[3] + dst[3])};
            }
          };
        case DIFFERENCE:
          return new Blender() {
            @Override
            public int[] blend(final int[] src, final int[] dst) {
              return new int[] {Math.abs(dst[0] - src[0]), Math.abs(dst[1] - src[1]), Math.abs(dst[2] - src[2]),
                  Math.min(255, src[3] + dst[3])};
            }
          };
      }
      throw new IllegalArgumentException("Blender not implement for " + composite.getMode().name());
    }
  }
}
