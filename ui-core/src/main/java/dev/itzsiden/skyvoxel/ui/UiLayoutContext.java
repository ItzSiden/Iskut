package dev.itzsiden.skyvoxel.ui;

/** Resolution-aware layout context. Design units scale from a 1280x720 reference viewport. */
public final class UiLayoutContext {
    private final int width;
    private final int height;
    private final float scale;

    public UiLayoutContext(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException("UI viewport dimensions must be positive.");
        this.width = width;
        this.height = height;
        this.scale = Math.max(0.5f, Math.min(3.0f, Math.min(width / 1280.0f, height / 720.0f)));
    }

    public int width() { return width; }
    public int height() { return height; }
    public float scale() { return scale; }
    public float px(float designUnits) { return designUnits * scale; }

    public UiRect resolve(UiPlacement placement, UiRect parent) {
        float w = px(placement.width());
        float h = px(placement.height());
        float ox = px(placement.offsetX());
        float oy = px(placement.offsetY());
        float x = switch (placement.anchor()) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> parent.x() + ox;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> parent.x() + (parent.width() - w) * 0.5f + ox;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> parent.x() + parent.width() - w - ox;
        };
        float y = switch (placement.anchor()) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> parent.y() + oy;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> parent.y() + (parent.height() - h) * 0.5f + oy;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> parent.y() + parent.height() - h - oy;
        };
        return new UiRect(x, y, w, h);
    }
}
