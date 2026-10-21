package dev.itzsiden.skyvoxel.render.api;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared Java2D rasterizer for the backend-neutral UI command list. */
public final class UiRasterizer {
    private static volatile Font gameFont;
    private static volatile boolean fontResolved;
    private static final Map<TextureData, BufferedImage> IMAGE_CACHE = new WeakHashMap<>();

    private UiRasterizer() { }

    /** Loads the game's optional bundled font. TTF/OTF are accepted; no native font install required. */
    private static Font resolveGameFont() {
        if (fontResolved) return gameFont;
        synchronized (UiRasterizer.class) {
            if (fontResolved) return gameFont;
            Path[] candidates = { Path.of("data", "fonts", "Inter.ttf"), Path.of("..", "data", "fonts", "Inter.ttf") };
            for (Path candidate : candidates) {
                if (!Files.isRegularFile(candidate)) continue;
                try {
                    gameFont = Font.createFont(Font.TRUETYPE_FONT, candidate.toFile());
                    System.out.println("[Iskut UI] Loaded Inter font: " + candidate.toAbsolutePath().normalize());
                    break;
                } catch (Exception ex) {
                    System.err.println("[Iskut UI] Could not load " + candidate + ": " + ex.getMessage());
                }
            }
            if (gameFont == null) {
                System.out.println("[Iskut UI] data/fonts/Inter.ttf not found; using system SansSerif fallback.");
            }
            fontResolved = true;
            return gameFont;
        }
    }

    public static BufferedImage rasterize(UiDrawList list, int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("UI raster dimensions must be positive.");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (list == null || list.isEmpty()) return image;
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            for (UiDrawList.Command command : list.commands()) {
                if (command instanceof UiDrawList.FillRect r) {
                    g.setColor(new Color(r.argb(), true));
                    if (r.radius() > 0) g.fill(new RoundRectangle2D.Float(r.x(), r.y(), r.width(), r.height(), r.radius() * 2, r.radius() * 2));
                    else g.fillRect(Math.round(r.x()), Math.round(r.y()), Math.round(r.width()), Math.round(r.height()));
                } else if (command instanceof UiDrawList.StrokeRect r) {
                    g.setColor(new Color(r.argb(), true));
                    g.setStroke(new BasicStroke(r.thickness()));
                    if (r.radius() > 0) g.draw(new RoundRectangle2D.Float(r.x(), r.y(), r.width(), r.height(), r.radius() * 2, r.radius() * 2));
                    else g.drawRect(Math.round(r.x()), Math.round(r.y()), Math.round(r.width()), Math.round(r.height()));
                } else if (command instanceof UiDrawList.Line line) {
                    g.setColor(new Color(line.argb(), true));
                    g.setStroke(new BasicStroke(line.thickness(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(Math.round(line.x1()), Math.round(line.y1()), Math.round(line.x2()), Math.round(line.y2()));
                } else if (command instanceof UiDrawList.Image itemImage) {
                    BufferedImage source = buffered(itemImage.texture());
                    java.awt.Composite oldComposite = g.getComposite();
                    Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                    g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, itemImage.alpha()));
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(source, Math.round(itemImage.x()), Math.round(itemImage.y()),
                        Math.max(1, Math.round(itemImage.width())), Math.max(1, Math.round(itemImage.height())), null);
                    g.setComposite(oldComposite);
                    if (oldInterpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
                } else if (command instanceof UiDrawList.Text t) {
                    int style = t.bold() ? Font.BOLD : Font.PLAIN;
                    Font gameFont = resolveGameFont();
                    Font base = gameFont != null ? gameFont : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
                    g.setFont(base.deriveFont(style, Math.max(1.0f, t.fontSize())));
                    int x = Math.round(t.x());
                    int y = Math.round(t.y()) + g.getFontMetrics().getAscent();
                    int textWidth = g.getFontMetrics().stringWidth(t.value());
                    if (t.align() == UiDrawList.TextAlign.CENTER) x -= textWidth / 2;
                    else if (t.align() == UiDrawList.TextAlign.RIGHT) x -= textWidth;
                    if (t.shadow()) {
                        g.setColor(new Color(0xC0000000, true));
                        g.drawString(t.value(), x + 1, y + 1);
                    }
                    g.setColor(new Color(t.argb(), true));
                    g.drawString(t.value(), x, y);
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage buffered(TextureData texture) {
        synchronized (IMAGE_CACHE) {
            BufferedImage cached = IMAGE_CACHE.get(texture);
            if (cached != null) return cached;
            BufferedImage image = new BufferedImage(texture.width(), texture.height(), BufferedImage.TYPE_INT_ARGB);
            int[] pixels = texture.pixels();
            image.setRGB(0, 0, texture.width(), texture.height(), pixels, 0, texture.width());
            IMAGE_CACHE.put(texture, image);
            return image;
        }
    }
}
