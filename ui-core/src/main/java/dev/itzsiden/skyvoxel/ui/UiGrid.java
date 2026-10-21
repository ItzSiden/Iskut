package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.ArrayList;
import java.util.List;

/** Simple row-major grid container, ready for inventory slots/hotbars. */
public final class UiGrid extends UiWidget {
    private final int columns;
    private final float gap;
    private final List<UiWidget> cells = new ArrayList<>();

    public UiGrid(int columns, float gap) {
        if (columns < 1) throw new IllegalArgumentException("Grid columns must be positive.");
        this.columns = columns;
        this.gap = Math.max(0, gap);
    }

    public UiGrid add(UiWidget cell) { cells.add(cell); return this; }
    public UiGrid clear() { cells.clear(); return this; }
    public int size() { return cells.size(); }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        if (cells.isEmpty()) return;
        int rows = (cells.size() + columns - 1) / columns;
        float gapPx = context.px(gap);
        float cellW = (bounds.width() - gapPx * (columns - 1)) / columns;
        float cellH = (bounds.height() - gapPx * (rows - 1)) / rows;
        for (int i = 0; i < cells.size(); i++) {
            UiWidget cell = cells.get(i);
            if (!cell.isVisible()) continue;
            int col = i % columns;
            int row = i / columns;
            UiRect cellBounds = new UiRect(bounds.x() + col * (cellW + gapPx),
                bounds.y() + row * (cellH + gapPx), cellW, cellH);
            cell.render(context, draw, cellBounds);
        }
    }
}
