package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.List;

final class MenuScreenSupport {
    private static final int TEXT = 0xFFF0F4F7;
    private static final int MUTED = 0xFFB4C0CB;

    private MenuScreenSupport() { }

    record Button(String id, String label, float x, float y, float width, float height) { }

    static void background(UiDrawList.Builder draw, TextureData image, int width, int height, int overlay) {
        draw.image(0, 0, width, height, image);
        draw.fillRect(0, 0, width, height, overlay);
    }

    static void button(UiDrawList.Builder draw, Button button, double mouseX, double mouseY, float scale) {
        boolean hovered = contains(button, mouseX, mouseY);
        int fill = hovered ? 0xE72B5360 : 0xD914202B;
        int border = hovered ? 0xFF8DEBFF : 0x99566D7D;
        draw.roundedRect(button.x(), button.y(), button.width(), button.height(), fill, 8 * scale);
        draw.strokeRoundedRect(button.x(), button.y(), button.width(), button.height(), border, 1.2f * scale, 8 * scale);
        draw.text(button.x() + button.width() / 2, button.y() + button.height() / 2 + 5 * scale,
            button.label(), 15 * scale, hovered ? 0xFFFFFFFF : TEXT, true, true, UiDrawList.TextAlign.CENTER);
    }

    static Button button(String id, String label, int width, int height, int index, int count) {
        float scale = Math.max(0.75f, Math.min(1.0f, Math.min(width / 1280.0f, height / 720.0f)));
        float buttonWidth = Math.min(width - 32.0f, 320.0f * scale);
        float buttonHeight = 48.0f * scale;
        float gap = 12.0f * scale;
        float totalHeight = count * buttonHeight + (count - 1) * gap;
        float x = (width - buttonWidth) / 2.0f;
        float firstY = height * 0.57f;
        if (firstY + totalHeight > height - 24.0f) firstY = height - totalHeight - 24.0f;
        return new Button(id, label, x, firstY + index * (buttonHeight + gap), buttonWidth, buttonHeight);
    }

    static Button centeredButton(String id, String label, int width, int height, float y, float scale) {
        float buttonWidth = Math.min(width - 32.0f, 320.0f * scale);
        float buttonHeight = 48.0f * scale;
        return new Button(id, label, (width - buttonWidth) / 2.0f, y, buttonWidth, buttonHeight);
    }

    static String hit(List<Button> buttons, double x, double y) {
        for (Button button : buttons) if (contains(button, x, y)) return button.id();
        return "";
    }

    static float scale(int width, int height) {
        return Math.max(0.75f, Math.min(1.0f, Math.min(width / 1280.0f, height / 720.0f)));
    }

    static int textColor() { return TEXT; }
    static int mutedColor() { return MUTED; }
    private static boolean contains(Button button, double x, double y) {
        return x >= button.x() && x <= button.x() + button.width()
            && y >= button.y() && y <= button.y() + button.height();
    }
}
