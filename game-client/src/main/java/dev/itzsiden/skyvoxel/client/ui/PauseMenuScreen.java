package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.List;

public final class PauseMenuScreen {
    public enum Action { NONE, RESUME, SETTINGS, MAIN_MENU }

    private final TextureData background;

    public PauseMenuScreen(TextureData background) {
        this.background = java.util.Objects.requireNonNull(background, "background");
    }

    public UiDrawList build(int width, int height, double mouseX, double mouseY) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        float scale = MenuScreenSupport.scale(safeWidth, safeHeight);
        List<MenuScreenSupport.Button> buttons = buttons(safeWidth, safeHeight);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        MenuScreenSupport.background(draw, background, safeWidth, safeHeight, 0xB8050A12);
        draw.text(safeWidth / 2.0f, safeHeight * 0.34f, "PAUSED", 28.0f * scale,
            MenuScreenSupport.textColor(), true, true, UiDrawList.TextAlign.CENTER);
        draw.text(safeWidth / 2.0f, safeHeight * 0.34f + 27.0f * scale, "YOUR ADVENTURE IS ON HOLD",
            9.0f * scale, MenuScreenSupport.mutedColor(), false, true, UiDrawList.TextAlign.CENTER);
        for (MenuScreenSupport.Button button : buttons) MenuScreenSupport.button(draw, button, mouseX, mouseY, scale);
        draw.text(safeWidth / 2.0f, safeHeight - 20.0f * scale, "PRESS ESC TO RESUME",
            9.0f * scale, 0xBFE0EAF0, false, true, UiDrawList.TextAlign.CENTER);
        return draw.build();
    }

    public Action handleClick(int width, int height, double mouseX, double mouseY) {
        return switch (MenuScreenSupport.hit(buttons(Math.max(1, width), Math.max(1, height)), mouseX, mouseY)) {
            case "resume" -> Action.RESUME;
            case "settings" -> Action.SETTINGS;
            case "main-menu" -> Action.MAIN_MENU;
            default -> Action.NONE;
        };
    }

    private static List<MenuScreenSupport.Button> buttons(int width, int height) {
        return List.of(
            MenuScreenSupport.button("resume", "RESUME", width, height, 0, 3),
            MenuScreenSupport.button("settings", "SETTINGS", width, height, 1, 3),
            MenuScreenSupport.button("main-menu", "BACK TO MAIN MENU", width, height, 2, 3));
    }
}
