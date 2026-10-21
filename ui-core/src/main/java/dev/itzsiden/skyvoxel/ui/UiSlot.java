package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Visual inventory/hotbar slot scaffold. Item identity and input handling belong to gameplay/UI input layers. */
public final class UiSlot extends UiWidget {
    private String label = "";
    private String count = "";
    private int background = 0xBB172330;
    private int border = 0xFF7E94A6;
    private boolean highlighted;

    public UiSlot setLabel(String label) { this.label = label == null ? "" : label; return this; }
    public UiSlot setCount(String count) { this.count = count == null ? "" : count; return this; }
    public UiSlot setColors(int background, int border) { this.background = background; this.border = border; return this; }
    public UiSlot setHighlighted(boolean highlighted) { this.highlighted = highlighted; return this; }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        draw.roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), background, context.px(3));
        int edge = highlighted ? 0xFFFFD46A : border;
        float thickness = highlighted ? 2.0f : 1.0f;
        draw.strokeRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), edge, context.px(thickness), context.px(3));
        if (!label.isEmpty()) draw.text(bounds.x() + context.px(5), bounds.y() + context.px(4), label, context.px(12), 0xFFF3F7FB, true, true, UiDrawList.TextAlign.LEFT);
        if (!count.isEmpty()) draw.text(bounds.x() + bounds.width() - context.px(5), bounds.y() + bounds.height() - context.px(4), count,
            context.px(13), 0xFFFFFFFF, true, true, UiDrawList.TextAlign.RIGHT);
    }
}
