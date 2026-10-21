package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads full-resolution GUI art into textures accepted by every renderer backend. */
public final class GuiAssets {
    private GuiAssets() { }

    public static TextureData loadTexture(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("GUI image was not found: " + file);
        BufferedImage source = ImageIO.read(file.toFile());
        if (source == null) throw new IOException("Unsupported GUI image: " + file);

        int width = nextPowerOfTwo(source.getWidth());
        int height = nextPowerOfTwo(source.getHeight());
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        TextureData texture = TextureData.fromImage(file.getFileName().toString(), normalized);
        normalized.flush();
        return texture;
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 0 || value > 8192) throw new IllegalArgumentException("GUI image dimension is out of range: " + value);
        int result = 1;
        while (result < value) result <<= 1;
        return result;
    }
}
