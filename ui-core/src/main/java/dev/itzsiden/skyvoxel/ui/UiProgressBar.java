package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Lightweight, resolution-aware horizontal resource bar for health, mana, stamina, and XP. */
public final class UiProgressBar extends UiWidget {
    private final int trackArgb;
    private final int fillArgb;
    private float value;
    private float maxValue;

    public UiProgressBar(float value, float maxValue, int trackArgb, int fillArgb) {
        this.trackArgb = trackArgb;
        this.fillArgb = fillArgb;
        setValue(value, maxValue);
    }

    public UiProgressBar setValue(float value, float maxValue) {
        if (!Float.isFinite(value) || !Float.isFinite(maxValue) || maxValue <= 0.0f) {
            throw new IllegalArgumentException("Progress value must be finite and max must be positive.");
        }
        this.maxValue = maxValue;
        this.value = Math.max(0.0f, Math.min(maxValue, value));
        return this;
    }

    public float fraction() { return maxValue <= 0.0f ? 0.0f : value / maxValue; }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        float h = Math.max(2.0f, bounds.height());
        float radius = Math.min(h * 0.5f, context.px(2.5f));
        draw.roundedRect(bounds.x(), bounds.y(), bounds.width(), h, trackArgb, radius);
        float fill = bounds.width() * fraction();
        if (fill > 0.0f) draw.roundedRect(bounds.x(), bounds.y(), fill, h, fillArgb, Math.min(radius, fill * 0.5f));
    }
}
