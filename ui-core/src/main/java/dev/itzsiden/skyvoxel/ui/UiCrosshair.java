package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Crisp, high-contrast first-person crosshair. */
public final class UiCrosshair extends UiWidget {
    private final float armLength;
    private final float gap;
    private final float thickness;
    private final int foreground;
    private final int outline;

    public UiCrosshair() { this(6.0f, 3.0f, 1.5f, 0xFFF7FAFF, 0xDD101820); }

    public UiCrosshair(float armLength, float gap, float thickness, int foreground, int outline) {
        this.armLength = Math.max(1, armLength);
        this.gap = Math.max(0, gap);
        this.thickness = Math.max(1, thickness);
        this.foreground = foreground;
        this.outline = outline;
    }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        float cx = bounds.x() + bounds.width() * 0.5f;
        float cy = bounds.y() + bounds.height() * 0.5f;
        float arm = context.px(armLength);
        float gapPx = context.px(gap);
        float thick = Math.max(1.0f, context.px(thickness));
        float outer = thick + Math.max(1.0f, context.px(1.25f));
        linePair(draw, cx, cy, arm, gapPx, outer, outline);
        linePair(draw, cx, cy, arm, gapPx, thick, foreground);
    }

    private static void linePair(UiDrawList.Builder draw, float cx, float cy, float arm, float gap, float thickness, int color) {
        draw.line(cx - gap - arm, cy, cx - gap, cy, color, thickness);
        draw.line(cx + gap, cy, cx + gap + arm, cy, color, thickness);
        draw.line(cx, cy - gap - arm, cx, cy - gap, color, thickness);
        draw.line(cx, cy + gap, cx, cy + gap + arm, color, thickness);
    }
}
