package dev.itzsiden.skyvoxel.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.util.Objects;

public final class UiLabel extends UiWidget {
    private String text;
    private final float fontSize;
    private final int argb;
    private final boolean bold;
    private final boolean shadow;
    private final UiDrawList.TextAlign align;

    public UiLabel(String text, float fontSize, int argb, boolean bold, boolean shadow) {
        this(text, fontSize, argb, bold, shadow, UiDrawList.TextAlign.LEFT);
    }

    public UiLabel(String text, float fontSize, int argb, boolean bold, boolean shadow, UiDrawList.TextAlign align) {
        this.text = Objects.requireNonNull(text, "text");
        if (fontSize <= 0) throw new IllegalArgumentException("Font size must be positive.");
        this.fontSize = fontSize;
        this.argb = argb;
        this.bold = bold;
        this.shadow = shadow;
        this.align = Objects.requireNonNull(align, "align");
    }

    public UiLabel setText(String text) { this.text = Objects.requireNonNull(text, "text"); return this; }
    public String text() { return text; }

    @Override public void render(UiLayoutContext context, UiDrawList.Builder draw, UiRect bounds) {
        float x = switch (align) {
            case LEFT -> bounds.x();
            case CENTER -> bounds.x() + bounds.width() * 0.5f;
            case RIGHT -> bounds.x() + bounds.width();
        };
        draw.text(x, bounds.y(), text, context.px(fontSize), argb, bold, shadow, align);
    }
}
