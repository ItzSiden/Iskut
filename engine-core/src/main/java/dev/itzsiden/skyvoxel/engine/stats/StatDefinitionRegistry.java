package dev.itzsiden.skyvoxel.engine.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Registry of known stats and UI metadata. Unknown stat IDs remain legal and default to zero. */
public final class StatDefinitionRegistry {
    private final Map<String, StatDefinition> definitions = new LinkedHashMap<>();

    public StatDefinitionRegistry register(StatDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String id = normalize(definition.id());
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate stat definition: " + id);
        }
        return this;
    }

    public Optional<StatDefinition> find(String id) { return Optional.ofNullable(definitions.get(normalize(id))); }
    public StatDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown stat definition: " + id));
    }
    public Collection<StatDefinition> all() { return Collections.unmodifiableCollection(definitions.values()); }
    public Map<String, StatDefinition> asMap() { return Collections.unmodifiableMap(definitions); }

    public static StatDefinitionRegistry standard() {
        return new StatDefinitionRegistry()
            .register(new StatDefinition("max_health", "Health", 100, 1, 1_000_000, true))
            .register(new StatDefinition("max_mana", "Mana", 60, 0, 1_000_000, true))
            .register(new StatDefinition("defense", "Defense", 0, 0, 1_000_000, true))
            .register(new StatDefinition("strength", "Strength", 10, 0, 1_000_000, true))
            .register(new StatDefinition("intelligence", "Intelligence", 0, 0, 1_000_000, true))
            .register(new StatDefinition("movement_speed", "Movement Speed", 1, 0.1f, 5, true))
            .register(new StatDefinition("damage_multiplier", "Damage", 1, 0.1f, 100, true))
            .register(new StatDefinition("weapon_damage", "Weapon Damage", 0, 0, 1_000_000, true))
            .register(new StatDefinition("crit_chance", "Critical Chance", 0, 0, 1, true))
            .register(new StatDefinition("crit_damage", "Critical Damage", 0.5f, 0, 100, true))
            .register(new StatDefinition("attack_speed", "Attack Speed", 1, 0.1f, 10, true))
            .register(new StatDefinition("health_regen", "Health Regeneration", 0, 0, 1_000_000, true))
            .register(new StatDefinition("mana_regen", "Mana Regeneration", 3, 0, 1_000_000, true))
            .register(new StatDefinition("mining_speed", "Mining Speed", 0, 0, 1_000_000, true))
            .register(new StatDefinition("magic_find", "Magic Find", 0, -100, 1_000_000, true))
            .register(new StatDefinition("ferocity", "Ferocity", 0, 0, 1_000_000, true))
            .register(new StatDefinition("fishing_speed", "Fishing Speed", 0, 0, 1_000_000, true))
            .register(new StatDefinition("mana_cost", "Mana Cost", 1, 0.05f, 10, false));
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Stat ID cannot be blank.");
        return id.strip().toLowerCase(Locale.ROOT);
    }
}
