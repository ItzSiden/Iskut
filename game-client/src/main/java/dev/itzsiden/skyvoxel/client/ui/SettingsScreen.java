package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.List;

public final class SettingsScreen {
    public enum Action { NONE, TOGGLE_QUALITY, BACK }

    private final TextureData background;

    public SettingsScreen(TextureData background) {
        this.background = java.util.Objects.requireNonNull(background, "background");
    }

    public UiDrawList build(int width, int height, double mouseX, double mouseY, RenderQuality quality) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        float scale = MenuScreenSupport.scale(safeWidth, safeHeight);
        MenuScreenSupport.Button qualityButton = qualityButton(safeWidth, safeHeight);
        MenuScreenSupport.Button backButton = backButton(safeWidth, safeHeight);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        MenuScreenSupport.background(draw, background, safeWidth, safeHeight, 0xBA050A12);
        draw.text(safeWidth / 2.0f, safeHeight * 0.27f, "SETTINGS", 26.0f * scale,
            MenuScreenSupport.textColor(), true, true, UiDrawList.TextAlign.CENTER);
        draw.text(safeWidth / 2.0f, safeHeight * 0.27f + 28.0f * scale, "CLIENT OPTIONS",
            9.0f * scale, MenuScreenSupport.mutedColor(), false, true, UiDrawList.TextAlign.CENTER);
        draw.text(safeWidth / 2.0f, qualityButton.y() - 31.0f * scale, "RENDER QUALITY",
            10.0f * scale, MenuScreenSupport.mutedColor(), true, false, UiDrawList.TextAlign.CENTER);
        MenuScreenSupport.button(draw,
            new MenuScreenSupport.Button(qualityButton.id(), quality.name(), qualityButton.x(), qualityButton.y(),
                qualityButton.width(), qualityButton.height()), mouseX, mouseY, scale);
        MenuScreenSupport.button(draw, backButton, mouseX, mouseY, scale);
        draw.text(safeWidth / 2.0f, backButton.y() + backButton.height() + 26.0f * scale,
            "QUALITY CHANGES MAY BE IGNORED BY SOME RENDERERS",
            8.0f * scale, MenuScreenSupport.mutedColor(), false, true, UiDrawList.TextAlign.CENTER);
        return draw.build();
    }

    public Action handleClick(int width, int height, double mouseX, double mouseY) {
        List<MenuScreenSupport.Button> buttons = List.of(
            qualityButton(Math.max(1, width), Math.max(1, height)),
            backButton(Math.max(1, width), Math.max(1, height)));
        return switch (MenuScreenSupport.hit(buttons, mouseX, mouseY)) {
            case "quality" -> Action.TOGGLE_QUALITY;
            case "back" -> Action.BACK;
            default -> Action.NONE;
        };
    }

    private static MenuScreenSupport.Button qualityButton(int width, int height) {
        float scale = MenuScreenSupport.scale(width, height);
        return MenuScreenSupport.centeredButton("quality", "QUALITY", width, height, height * 0.45f, scale);
    }

    private static MenuScreenSupport.Button backButton(int width, int height) {
        float scale = MenuScreenSupport.scale(width, height);
        return MenuScreenSupport.centeredButton("back", "BACK", width, height, height * 0.64f, scale);
    }
}
