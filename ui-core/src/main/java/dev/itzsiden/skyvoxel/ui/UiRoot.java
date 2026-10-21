package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Root UI tree. Widgets use anchors and reference-resolution design units. */
public final class UiRoot {
    private final List<UiPlacement> children = new ArrayList<>();

    public UiRoot add(UiWidget widget, UiAnchor anchor, float x, float y, float width, float height) {
        children.add(new UiPlacement(widget, anchor, x, y, width, height));
        return this;
    }

    public boolean remove(UiWidget widget) {
        return children.removeIf(p -> p.widget() == widget);
    }

    public List<UiPlacement> children() { return List.copyOf(children); }

    public UiDrawList build(int viewportWidth, int viewportHeight) {
        UiLayoutContext context = new UiLayoutContext(viewportWidth, viewportHeight);
        UiRect viewport = new UiRect(0, 0, viewportWidth, viewportHeight);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        for (UiPlacement placement : children) {
            if (!placement.widget().isVisible()) continue;
            UiRect bounds = context.resolve(placement, viewport);
            placement.widget().render(context, draw, bounds);
        }
        return draw.build();
    }
}
