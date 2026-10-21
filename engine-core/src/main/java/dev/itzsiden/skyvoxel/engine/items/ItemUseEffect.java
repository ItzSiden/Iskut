package dev.itzsiden.skyvoxel.engine.items;

import java.util.Locale;
import java.util.Objects;

/**
 * Declarative effect attached to an item definition. The effect ID is intentionally open-ended so new
 * effect handlers can be registered without changing inventory storage or item definitions.
 *
 * Current built-in handlers: heal and restore_mana. The target and duration fields reserve a stable,
 * data-driven shape for stat buffs and other future effects.
 */
public record ItemUseEffect(String type, String target, float amount, float durationSeconds) {
    public ItemUseEffect {
        if (type == null || !type.strip().matches("[a-z][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("Use-effect type must be a lowercase stable identifier.");
        }
        type = type.strip().toLowerCase(Locale.ROOT);
        target = target == null ? "" : target.strip().toLowerCase(Locale.ROOT);
        if (!Float.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Use-effect amount must be finite and positive.");
        if (!Float.isFinite(durationSeconds) || durationSeconds < 0) throw new IllegalArgumentException("Use-effect duration cannot be negative.");
    }

    public static ItemUseEffect parse(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        String[] parts = encoded.split("\\|", -1);
        if (parts.length == 2) return new ItemUseEffect(parts[0].strip().toLowerCase(Locale.ROOT), "", Float.parseFloat(parts[1].strip()), 0);
        if (parts.length == 4) return new ItemUseEffect(parts[0].strip().toLowerCase(Locale.ROOT), parts[1].strip(),
            Float.parseFloat(parts[2].strip()), Float.parseFloat(parts[3].strip()));
        throw new IllegalArgumentException("use_effect format is TYPE|amount or TYPE|target|amount|durationSeconds");
    }
}
