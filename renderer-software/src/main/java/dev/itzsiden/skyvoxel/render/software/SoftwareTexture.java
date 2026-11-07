package dev.itzsiden.skyvoxel.render.software;

import dev.itzsiden.skyvoxel.render.api.RenderTexture;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU texture with compact mipmaps and lazily generated pre-shaded copies.
 *
 * <p>Mipmaps reduce distant pixel-art shimmer and texture moire without smoothing close-up texels.
 * Every level keeps power-of-two dimensions, so the raster inner loop uses masks and shifts rather
 * than modulo. Shade variants are cached per mip and built on the submitting thread before workers
 * begin, avoiding races while the bands are rasterized in parallel.
 */
final class SoftwareTexture implements RenderTexture {
    static final int LEVELS = 16;

    private static final class Mip {
        final int width, height, widthMask, heightMask, widthShift;
        final int[] pixels;
        final int[][] shades = new int[LEVELS][];

        Mip(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.widthMask = width - 1;
            this.heightMask = height - 1;
            this.widthShift = Integer.numberOfTrailingZeros(width);
            this.pixels = pixels;
            shades[LEVELS - 1] = pixels;
        }

        int[] shade(int level) {
            int[] cached = shades[level];
            if (cached != null) return cached;
            int factor = Math.round(level * 256.0f / (LEVELS - 1));
            int[] out = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                int r = (((c >>> 16) & 255) * factor) >> 8;
                int g = (((c >>> 8) & 255) * factor) >> 8;
                int b = ((c & 255) * factor) >> 8;
                out[i] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
            shades[level] = out;
            return out;
        }
    }

    private final SoftwareRenderer owner;
    final int slot;
    final int width;
    final int height;
    final boolean cutout;
    private final Mip[] mipmaps;
    private boolean closed;

    SoftwareTexture(SoftwareRenderer owner, int slot, TextureData data) {
        this.owner = owner;
        this.slot = slot;
        this.width = data.width();
        this.height = data.height();
        this.cutout = data.hasTransparency();

        List<Mip> generated = new ArrayList<>();
        int w = data.width(), h = data.height();
        int[] pixels = data.pixels();
        generated.add(new Mip(w, h, pixels));
        // A model atlas contains several independent texture islands. Global mip generation
        // averages across atlas padding and transparent canvas, creating visible colour and
        // alpha glitches at distance. Keep model atlases at LOD 0; ordinary world/UI textures
        // continue to use their existing mip chain.
        boolean isolatedModelAtlas = data.name().startsWith("model-atlas-no-mip");
        while (!isolatedModelAtlas && (w > 1 || h > 1)) {
            int nw = Math.max(1, w >>> 1), nh = Math.max(1, h >>> 1);
            int[] down = downsample(pixels, w, h, nw, nh);
            generated.add(new Mip(nw, nh, down));
            pixels = down; w = nw; h = nh;
        }
        mipmaps = generated.toArray(Mip[]::new);
    }

    int maxMipLevel() { return mipmaps.length - 1; }
    int widthMask(int mip) { return mipmaps[mip].widthMask; }
    int heightMask(int mip) { return mipmaps[mip].heightMask; }
    int widthShift(int mip) { return mipmaps[mip].widthShift; }
    int[] level(int level, int mip) { return mipmaps[mip].shade(level); }
    int[] levelOrNull(int level, int mip) { return mipmaps[mip].shades[level]; }

    private static int[] downsample(int[] source, int width, int height, int outWidth, int outHeight) {
        int[] out = new int[outWidth * outHeight];
        for (int y = 0; y < outHeight; y++) {
            int sy = y * 2;
            for (int x = 0; x < outWidth; x++) {
                int sx = x * 2;
                int c00 = source[Math.min(height - 1, sy) * width + Math.min(width - 1, sx)];
                int c10 = source[Math.min(height - 1, sy) * width + Math.min(width - 1, sx + 1)];
                int c01 = source[Math.min(height - 1, sy + 1) * width + Math.min(width - 1, sx)];
                int c11 = source[Math.min(height - 1, sy + 1) * width + Math.min(width - 1, sx + 1)];
                int a = (((c00 >>> 24) & 255) + ((c10 >>> 24) & 255) + ((c01 >>> 24) & 255) + ((c11 >>> 24) & 255) + 2) >> 2;
                int r = (((c00 >>> 16) & 255) + ((c10 >>> 16) & 255) + ((c01 >>> 16) & 255) + ((c11 >>> 16) & 255) + 2) >> 2;
                int g = (((c00 >>> 8) & 255) + ((c10 >>> 8) & 255) + ((c01 >>> 8) & 255) + ((c11 >>> 8) & 255) + 2) >> 2;
                int b = ((c00 & 255) + (c10 & 255) + (c01 & 255) + (c11 & 255) + 2) >> 2;
                out[y * outWidth + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    boolean isClosed() { return closed; }
    boolean belongsTo(SoftwareRenderer renderer) { return owner == renderer; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        owner.releaseTexture(this);
    }
}
