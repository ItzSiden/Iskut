package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.render.api.TextureData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Packs model-local pixel textures (legacy .vmodel or imported Blockbench Java JSON) into a nearest-filtered atlas. */
final class VModelTextureAtlas {
    private static final int PADDING = 2;

    record PixelTexture(String id, int width, int height, int[] argb) {
        PixelTexture {
            id = Objects.requireNonNull(id, "id");
            if (id.isBlank()) throw new IllegalArgumentException("Texture id cannot be blank.");
            if (width < 1 || height < 1 || width > 512 || height > 512)
                throw new IllegalArgumentException("Pixel textures must be between 1x1 and 512x512: " + width + "x" + height);
            if (argb == null || argb.length != width * height) throw new IllegalArgumentException("Pixel count does not match texture dimensions: " + id);
            argb = argb.clone();
        }
        @Override public int[] argb() { return argb.clone(); }
        int pixelAt(int x, int y) { return argb[y * width + x]; }
    }

    record Region(float u0, float v0, float u1, float v1, int x, int y, int width, int height) { }
    record Built(TextureData texture, Map<String, Region> regions) { }

    private VModelTextureAtlas() { }

    static Built build(Map<String, PixelTexture> textures) {
        if (textures == null || textures.isEmpty()) throw new IllegalArgumentException("A textured .vmodel needs at least one pixel texture.");
        int maxDimension = 1;
        for (PixelTexture texture : textures.values()) maxDimension = Math.max(maxDimension, Math.max(texture.width(), texture.height()));
        int cell = maxDimension + PADDING * 2;
        int columns = (int) Math.ceil(Math.sqrt(textures.size()));
        int rows = (textures.size() + columns - 1) / columns;
        int width = nextPowerOfTwo(columns * cell);
        int height = nextPowerOfTwo(rows * cell);
        if (width > 2048 || height > 2048) throw new IllegalArgumentException("Model texture atlas exceeds 2048x2048; reduce texture count/size.");
        int[] pixels = new int[width * height];
        // Transparent unused atlas pixels make accidental sampling obvious instead of hiding UV bugs.
        java.util.Arrays.fill(pixels, 0x00000000);
        Map<String, Region> regions = new LinkedHashMap<>();
        int index = 0;
        for (PixelTexture source : textures.values()) {
            int cellX = (index % columns) * cell;
            int cellY = (index / columns) * cell;
            int tx = cellX + PADDING, ty = cellY + PADDING;
            int[] sourcePixels = source.argb();
            // Edge padding protects the nearest/linear sampling border and keeps UVs away from neighbours.
            for (int py = -PADDING; py < source.height() + PADDING; py++) {
                int sy = Math.max(0, Math.min(source.height() - 1, py));
                for (int px = -PADDING; px < source.width() + PADDING; px++) {
                    int sx = Math.max(0, Math.min(source.width() - 1, px));
                    pixels[(ty + py) * width + tx + px] = sourcePixels[sy * source.width() + sx];
                }
            }
            regions.put(source.id(), new Region(
                (tx + 0.5f) / width, (ty + 0.5f) / height,
                (tx + source.width() - 0.5f) / width, (ty + source.height() - 0.5f) / height,
                tx, ty, source.width(), source.height()));
            index++;
        }
        return new Built(new TextureData("vmodel-atlas", width, height, pixels), Collections.unmodifiableMap(regions));
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }
}
