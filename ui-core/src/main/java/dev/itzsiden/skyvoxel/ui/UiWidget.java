package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Base class for reusable UI widgets. Subclasses emit backend-neutral draw commands. */
public abstract class UiWidget {
    private boolean visible = true;

    public final boolean isVisible() { return visible; }
    public final UiWidget setVisible(boolean visible) { this.visible = visible; return this; }

    public abstract void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds);
}
