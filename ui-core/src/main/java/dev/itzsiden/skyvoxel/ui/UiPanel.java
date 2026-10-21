package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.ArrayList;
import java.util.List;

/** Container widget with a background/border and independently anchored child widgets. */
public final class UiPanel extends UiWidget {
    private final int backgroundArgb;
    private final int borderArgb;
    private final float borderThickness;
    private final float radius;
    private final List<UiPlacement> children = new ArrayList<>();

    public UiPanel(int backgroundArgb, int borderArgb, float borderThickness, float radius) {
        this.backgroundArgb = backgroundArgb;
        this.borderArgb = borderArgb;
        this.borderThickness = Math.max(0, borderThickness);
        this.radius = Math.max(0, radius);
    }

    public UiPanel add(UiWidget widget, UiAnchor anchor, float x, float y, float width, float height) {
        children.add(new UiPlacement(widget, anchor, x, y, width, height));
        return this;
    }

    public List<UiPlacement> children() { return List.copyOf(children); }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        if ((backgroundArgb >>> 24) != 0) draw.roundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), backgroundArgb, context.px(radius));
        if ((borderArgb >>> 24) != 0 && borderThickness > 0) draw.strokeRoundedRect(bounds.x(), bounds.y(), bounds.width(), bounds.height(), borderArgb, context.px(borderThickness), context.px(radius));
        for (UiPlacement child : children) {
            if (!child.widget().isVisible()) continue;
            UiRect childBounds = context.resolve(child, bounds);
            child.widget().render(context, draw, childBounds);
        }
    }
}
