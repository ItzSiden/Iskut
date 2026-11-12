package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Modal harbor ship-selection overlay. */
public final class ShipMenuOverlay {
    public enum Action { NONE, CLOSE, SUMMON }

    private record Layout(float x, float y, float width, float height,
                          float closeX, float closeY, float buttonX, float buttonY,
                          float buttonWidth, float buttonHeight, float scale) { }

    public UiDrawList build(int width, int height, double mouseX, double mouseY) {
        Layout l = layout(width, height);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        draw.fillRect(0, 0, width, height, 0x99050B12);
        draw.roundedRect(l.x(), l.y(), l.width(), l.height(), 0xF014202A, 14.0f * l.scale());
        draw.strokeRoundedRect(l.x(), l.y(), l.width(), l.height(), 0xFF527181, 1.5f * l.scale(), 14.0f * l.scale());

        draw.fillRect(l.x() + 1.0f, l.y() + 1.0f, l.width() - 2.0f, 4.0f * l.scale(), 0xFF58C9D5);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 43.0f * l.scale(),
            "HARBOR MASTER", 10.0f * l.scale(), 0xFF8DD9EA, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 79.0f * l.scale(),
            "Ship Menu", 27.0f * l.scale(), 0xFFF4F6F8, true, true, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 106.0f * l.scale(),
            "Captain Rowan  /  Iskut Harbor", 12.0f * l.scale(), 0xFFB5C5D1, false, false, UiDrawList.TextAlign.LEFT);

        draw.roundedRect(l.x() + 28.0f * l.scale(), l.y() + 132.0f * l.scale(),
            l.width() - 56.0f * l.scale(), 92.0f * l.scale(), 0xD91B2A37, 9.0f * l.scale());
        draw.roundedRect(l.x() + 46.0f * l.scale(), l.y() + 151.0f * l.scale(),
            56.0f * l.scale(), 54.0f * l.scale(), 0xFF294B58, 8.0f * l.scale());
        draw.text(l.x() + 74.0f * l.scale(), l.y() + 186.0f * l.scale(),
            "SAIL", 13.0f * l.scale(), 0xFF8DEBFF, true, true, UiDrawList.TextAlign.CENTER);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 164.0f * l.scale(),
            "HARBOR CUTTER", 14.0f * l.scale(), 0xFFF0F4F7, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 188.0f * l.scale(),
            "A nimble vessel for the outer islands.", 11.0f * l.scale(), 0xFFB5C5D1, false, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 207.0f * l.scale(),
            "No fare required", 10.0f * l.scale(), 0xFF79D4AF, true, false, UiDrawList.TextAlign.LEFT);

        boolean hovered = contains(mouseX, mouseY, l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight());
        draw.roundedRect(l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight(),
            hovered ? 0xFF287487 : 0xFF1D5969, 8.0f * l.scale());
        draw.strokeRoundedRect(l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight(),
            hovered ? 0xFFB3F4FF : 0xFF4F9CAC, 1.2f * l.scale(), 8.0f * l.scale());
        draw.text(l.buttonX() + l.buttonWidth() * 0.5f, l.buttonY() + l.buttonHeight() * 0.5f + 5.0f * l.scale(),
            "SUMMON VESSEL", 14.0f * l.scale(), 0xFFFFFFFF, true, true, UiDrawList.TextAlign.CENTER);

        draw.roundedRect(l.closeX(), l.closeY(), 30.0f * l.scale(), 30.0f * l.scale(), 0xFF293945, 7.0f * l.scale());
        draw.text(l.closeX() + 15.0f * l.scale(), l.closeY() + 20.0f * l.scale(),
            "X", 13.0f * l.scale(), 0xFFD6E1E8, true, false, UiDrawList.TextAlign.CENTER);
        draw.text(l.x() + l.width() * 0.5f, l.y() + l.height() - 15.0f * l.scale(),
            "W / S  SAIL     A / D  STEER     E  LEAVE THE HELM NEAR LAND",
            9.0f * l.scale(), 0xFF8FA3B0, true, false, UiDrawList.TextAlign.CENTER);
        return draw.build();
    }

    public UiDrawList buildHud(int width, int height, float speed) {
        float scale = scale(width, height);
        float w = 430.0f * scale;
        float h = 38.0f * scale;
        float x = (width - w) * 0.5f;
        float y = height - 68.0f * scale;
        UiDrawList.Builder draw = new UiDrawList.Builder();
        draw.roundedRect(x, y, w, h, 0xC9121D27, 8.0f * scale);
        draw.strokeRoundedRect(x, y, w, h, 0x7755C9D5, 1.0f * scale, 8.0f * scale);
        draw.text(x + 12.0f * scale, y + 23.0f * scale,
            "HELM  /  W S SAIL  •  A D STEER  •  E DOCK", 10.0f * scale,
            0xFFEAF2F6, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(x + w - 12.0f * scale, y + 23.0f * scale,
            String.format(java.util.Locale.ROOT, "%.1f u/s", Math.abs(speed)),
            10.0f * scale, 0xFF8DEBFF, true, false, UiDrawList.TextAlign.RIGHT);
        return draw.build();
    }

    public Action handleClick(int width, int height, double mouseX, double mouseY) {
        Layout l = layout(width, height);
        if (contains(mouseX, mouseY, l.closeX(), l.closeY(), 30.0f * l.scale(), 30.0f * l.scale())) {
            return Action.CLOSE;
        }
        if (contains(mouseX, mouseY, l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight())) {
            return Action.SUMMON;
        }
        return Action.NONE;
    }

    private static Layout layout(int width, int height) {
        float scale = scale(width, height);
        float panelWidth = Math.min(width - 28.0f, 520.0f * scale);
        float panelHeight = Math.min(height - 28.0f, 360.0f * scale);
        float x = (width - panelWidth) * 0.5f;
        float y = (height - panelHeight) * 0.5f;
        float buttonWidth = Math.min(panelWidth - 56.0f * scale, 300.0f * scale);
        float buttonHeight = 46.0f * scale;
        return new Layout(x, y, panelWidth, panelHeight,
            x + panelWidth - 48.0f * scale, y + 18.0f * scale,
            x + (panelWidth - buttonWidth) * 0.5f, y + panelHeight - 76.0f * scale,
            buttonWidth, buttonHeight, scale);
    }

    private static float scale(int width, int height) {
        return Math.max(0.72f, Math.min(1.0f, Math.min(width / 1280.0f, height / 720.0f)));
    }

    private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
}
