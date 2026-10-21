package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.RenderFrame;
import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.SkyboxSettings;
import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

public final class LoadingScreen {
    private final TextureData background;

    public LoadingScreen(TextureData background) {
        this.background = java.util.Objects.requireNonNull(background, "background");
    }

    public UiDrawList build(int width, int height, float progress, String status) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        float value = Math.max(0.0f, Math.min(1.0f, progress));
        float scale = MenuScreenSupport.scale(safeWidth, safeHeight);
        float barWidth = Math.min(520.0f * scale, safeWidth - 48.0f);
        float barHeight = 12.0f * scale;
        float x = (safeWidth - barWidth) / 2.0f;
        float y = safeHeight * 0.79f;

        UiDrawList.Builder draw = new UiDrawList.Builder();
        MenuScreenSupport.background(draw, background, safeWidth, safeHeight, 0x64050A12);
        draw.text(safeWidth / 2.0f, y - 43.0f * scale, "LOADING ISKUT", 21.0f * scale,
            MenuScreenSupport.textColor(), true, true, UiDrawList.TextAlign.CENTER);
        draw.text(safeWidth / 2.0f, y - 19.0f * scale, status == null ? "Preparing game..." : status,
            11.0f * scale, MenuScreenSupport.mutedColor(), false, true, UiDrawList.TextAlign.CENTER);
        draw.roundedRect(x, y, barWidth, barHeight, 0xD91A2631, 6.0f * scale);
        draw.strokeRoundedRect(x, y, barWidth, barHeight, 0xFF91A6B5, 1.0f * scale, 6.0f * scale);
        draw.roundedRect(x + 2.0f * scale, y + 2.0f * scale,
            Math.max(0.0f, (barWidth - 4.0f * scale) * value), Math.max(1.0f, barHeight - 4.0f * scale),
            0xFF55C9D5, 4.0f * scale);
        draw.text(safeWidth / 2.0f, y + 33.0f * scale, Math.round(value * 100.0f) + "%",
            10.0f * scale, MenuScreenSupport.mutedColor(), true, false, UiDrawList.TextAlign.CENTER);
        return draw.build();
    }

    public void present(Renderer renderer, RenderWindow window, float progress, String status) {
        window.pollEvents();
        int width = Math.max(1, window.uiWidth());
        int height = Math.max(1, window.uiHeight());
        renderer.beginFrame(frame());
        renderer.drawUi(build(width, height, progress, status));
        renderer.endFrame();
    }

    private static RenderFrame frame() {
        SkyboxSettings sky = SkyboxSettings.DAYLIGHT;
        return new RenderFrame(identity(), sky.horizonRed(), sky.horizonGreen(), sky.horizonBlue(), 0, 0, sky);
    }

    private static float[] identity() {
        return new float[] {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
    }
}
