package dev.itzsiden.skyvoxel.render.api;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Immutable CPU-side texture (or texture atlas): ARGB pixels, power-of-two dimensions.
 * Backends upload it with {@link Renderer#createTexture(TextureData)}.
 */
public final class TextureData {
    private final String name;
    private final int width;
    private final int height;
    private final int[] argb;
    private final boolean hasTransparency;

    public TextureData(String name, int width, int height, int[] argb) {
        this.name = Objects.requireNonNull(name, "name");
        if (!isPowerOfTwo(width) || !isPowerOfTwo(height)) {
            throw new IllegalArgumentException("Texture dimensions must be powers of two: " + width + "x" + height);
        }
        if (width > 8192 || height > 8192) throw new IllegalArgumentException("Texture is too large.");
        if (argb == null || argb.length != width * height) {
            throw new IllegalArgumentException("Pixel array must contain width*height ARGB values.");
        }
        this.width = width;
        this.height = height;
        this.argb = argb.clone();
        boolean transparent = false;
        for (int pixel : argb) if ((pixel >>> 24) != 0xFF) { transparent = true; break; }
        this.hasTransparency = transparent;
    }

    public static TextureData fromImage(String name, BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[] pixels = new int[w * h];
        image.getRGB(0, 0, w, h, pixels, 0, w);
        return new TextureData(name, w, h, pixels);
    }

    private static boolean isPowerOfTwo(int v) { return v > 0 && (v & (v - 1)) == 0; }

    public String name() { return name; }
    public int width() { return width; }
    public int height() { return height; }
    public int[] pixels() { return argb.clone(); }
    /** True when any pixel has alpha below 255 (backends then use alpha-tested "cutout" drawing). */
    public boolean hasTransparency() { return hasTransparency; }
}
