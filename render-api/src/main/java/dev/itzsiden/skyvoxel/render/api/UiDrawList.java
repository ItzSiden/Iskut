package dev.itzsiden.skyvoxel.render.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable, backend-neutral 2D overlay commands. Coordinates are physical viewport pixels. */
public final class UiDrawList {
    public enum TextAlign { LEFT, CENTER, RIGHT }

    public sealed interface Command permits FillRect, StrokeRect, Line, Text, Image { }
    public record FillRect(float x, float y, float width, float height, int argb, float radius) implements Command { }
    public record StrokeRect(float x, float y, float width, float height, int argb, float thickness, float radius) implements Command { }
    public record Line(float x1, float y1, float x2, float y2, int argb, float thickness) implements Command { }
    public record Text(float x, float y, String value, float fontSize, int argb,
                       boolean bold, boolean shadow, TextAlign align) implements Command {
        public Text {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(align, "align");
        }
    }
    /** Scaled RGBA image command for model-generated inventory icons and future UI art. */
    public record Image(float x, float y, float width, float height, TextureData texture, float alpha) implements Command {
        public Image {
            Objects.requireNonNull(texture, "texture");
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || width < 0 || height < 0 || !Float.isFinite(alpha) || alpha < 0 || alpha > 1) {
                throw new IllegalArgumentException("UI image bounds/alpha are invalid.");
            }
        }
    }

    public static final UiDrawList EMPTY = new UiDrawList(List.of());
    private final List<Command> commands;

    private UiDrawList(List<Command> commands) {
        this.commands = Collections.unmodifiableList(new ArrayList<>(commands));
    }

    public List<Command> commands() { return commands; }
    public boolean isEmpty() { return commands.isEmpty(); }

    public static final class Builder {
        private final List<Command> commands = new ArrayList<>();

        public Builder fillRect(float x, float y, float width, float height, int argb) {
            return roundedRect(x, y, width, height, argb, 0.0f);
        }
        public Builder roundedRect(float x, float y, float width, float height, int argb, float radius) {
            if (width > 0 && height > 0 && (argb >>> 24) != 0) commands.add(new FillRect(x, y, width, height, argb, Math.max(0, radius)));
            return this;
        }
        public Builder strokeRect(float x, float y, float width, float height, int argb, float thickness) {
            return strokeRoundedRect(x, y, width, height, argb, thickness, 0.0f);
        }
        public Builder strokeRoundedRect(float x, float y, float width, float height, int argb, float thickness, float radius) {
            if (width > 0 && height > 0 && thickness > 0 && (argb >>> 24) != 0) commands.add(new StrokeRect(x, y, width, height, argb, thickness, Math.max(0, radius)));
            return this;
        }
        public Builder line(float x1, float y1, float x2, float y2, int argb, float thickness) {
            if (thickness > 0 && (argb >>> 24) != 0) commands.add(new Line(x1, y1, x2, y2, argb, thickness));
            return this;
        }
        public Builder text(float x, float y, String value, float fontSize, int argb,
                            boolean bold, boolean shadow, TextAlign align) {
            if (value != null && !value.isEmpty() && fontSize > 0 && (argb >>> 24) != 0) {
                commands.add(new Text(x, y, value, fontSize, argb, bold, shadow, align));
            }
            return this;
        }
        public Builder image(float x, float y, float width, float height, TextureData texture) {
            return image(x, y, width, height, texture, 1.0f);
        }
        public Builder image(float x, float y, float width, float height, TextureData texture, float alpha) {
            if (texture != null && width > 0 && height > 0 && alpha > 0) {
                commands.add(new Image(x, y, width, height, texture, Math.min(1.0f, alpha)));
            }
            return this;
        }
        /** Appends commands from another immutable draw list, preserving painter's order. */
        public Builder addAll(UiDrawList other) {
            if (other != null && !other.isEmpty()) commands.addAll(other.commands());
            return this;
        }
        public UiDrawList build() { return commands.isEmpty() ? EMPTY : new UiDrawList(commands); }
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof UiDrawList that && commands.equals(that.commands);
    }
    @Override public int hashCode() { return commands.hashCode(); }
}
