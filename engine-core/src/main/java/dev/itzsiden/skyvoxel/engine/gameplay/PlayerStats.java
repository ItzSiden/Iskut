package dev.itzsiden.skyvoxel.engine.gameplay;

import dev.itzsiden.skyvoxel.engine.stats.ModifierOperation;
import dev.itzsiden.skyvoxel.engine.stats.StatCalculator;
import dev.itzsiden.skyvoxel.engine.stats.StatDefinition;
import dev.itzsiden.skyvoxel.engine.stats.StatDefinitionRegistry;
import dev.itzsiden.skyvoxel.engine.stats.StatModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime resource state plus a source-aware, extensible stat aggregator. Existing convenience accessors remain
 * for combat/movement code; future stats need only a definition and modifiers, not another PlayerStats field.
 * All mutations are intended to run on the game thread or authoritative server thread.
 */
public final class PlayerStats {
    private final float baseMaxHealth;
    private final float baseMaxMana;
    private final float baseDefense;
    private final float baseStrength;
    private final float baseIntelligence;
    private final EquipmentLoadout equipment;
    private final StatDefinitionRegistry definitions;
    private final Map<String, Float> additionalBaseStats = new LinkedHashMap<>();
    private final Map<String, List<StatModifier>> modifierSources = new LinkedHashMap<>();
    private StatModifiers activeBuffs = StatModifiers.ZERO;
    private float health;
    private float mana;

    public PlayerStats(EquipmentLoadout equipment) {
        this(100, 60, 0, 10, 0, equipment, StatDefinitionRegistry.standard());
    }

    public PlayerStats(float baseMaxHealth, float baseMaxMana, float baseDefense,
                       float baseStrength, float baseIntelligence, EquipmentLoadout equipment) {
        this(baseMaxHealth, baseMaxMana, baseDefense, baseStrength, baseIntelligence, equipment, StatDefinitionRegistry.standard());
    }

    public PlayerStats(float baseMaxHealth, float baseMaxMana, float baseDefense,
                       float baseStrength, float baseIntelligence, EquipmentLoadout equipment,
                       StatDefinitionRegistry definitions) {
        if (!Float.isFinite(baseMaxHealth) || baseMaxHealth <= 0 || !Float.isFinite(baseMaxMana) || baseMaxMana < 0
            || !Float.isFinite(baseDefense) || baseDefense < 0 || !Float.isFinite(baseStrength) || baseStrength < 0
            || !Float.isFinite(baseIntelligence) || baseIntelligence < 0) {
            throw new IllegalArgumentException("Base stats must be finite and non-negative; max health must be positive.");
        }
        this.baseMaxHealth = baseMaxHealth;
        this.baseMaxMana = baseMaxMana;
        this.baseDefense = baseDefense;
        this.baseStrength = baseStrength;
        this.baseIntelligence = baseIntelligence;
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
        this.health = maxHealth();
        this.mana = maxMana();
    }

    public EquipmentLoadout equipment() { return equipment; }
    public StatDefinitionRegistry definitions() { return definitions; }
    public float maxHealth() { return stat("max_health"); }
    public float maxMana() { return stat("max_mana"); }
    public float defense() { return stat("defense"); }
    public float strength() { return stat("strength"); }
    public float intelligence() { return stat("intelligence"); }
    public float health() { return health; }
    public float mana() { return mana; }
    public float healthRatio() { return Math.max(0, Math.min(1, health / maxHealth())); }
    public float manaRatio() { return maxMana() <= 0 ? 0 : Math.max(0, Math.min(1, mana / maxMana())); }
    public boolean alive() { return health > 0; }
    public float movementSpeedMultiplier() { return Math.max(0.5f, Math.min(2.5f, stat("movement_speed"))); }
    public float attackSpeedMultiplier() { return Math.max(0.1f, Math.min(10.0f, stat("attack_speed"))); }
    public float criticalChance() { return Math.max(0.0f, Math.min(1.0f, stat("crit_chance"))); }
    public float criticalDamage() { return Math.max(0.0f, stat("crit_damage")); }

    /** Computes any registered or custom stat ID on demand. Unknown stat IDs are allowed and default to zero. */
    public float stat(String statId) {
        String id = normalizeStatId(statId);
        float base = baseValue(id);
        return StatCalculator.calculate(id, base, allModifiers(), definitions);
    }

    public Map<String, Float> visibleStats() {
        Map<String, Float> result = new LinkedHashMap<>();
        for (StatDefinition definition : definitions.all()) {
            if (definition.shownInInventory()) result.put(definition.id(), stat(definition.id()));
        }
        for (String id : additionalBaseStats.keySet()) {
            if (definitions.find(id).isEmpty()) result.put(id, stat(id));
        }
        return Map.copyOf(result);
    }

    public void setBaseStat(String statId, float value) {
        if (statId == null || statId.isBlank() || !Float.isFinite(value)) throw new IllegalArgumentException("Base stat ID/value is invalid.");
        String id = normalizeStatId(statId);
        additionalBaseStats.put(id, value);
        clampVitals();
    }

    /** Snapshot only the base layer, never the derived result. Equipment/buffs will be recomputed after load. */
    public Map<String, Float> baseStatsSnapshot() {
        Map<String, Float> result = new LinkedHashMap<>();
        for (StatDefinition definition : definitions.all()) result.put(definition.id(), baseValue(definition.id()));
        result.putAll(additionalBaseStats);
        return Map.copyOf(result);
    }

    /** Replace saved base stat values before modifier sources are rebuilt from inventory/effects. */
    public void applyBaseStatsSnapshot(Map<String, Float> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<String, Float> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Float> entry : snapshot.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || !Float.isFinite(entry.getValue()))
                throw new IllegalArgumentException("Invalid persisted base stat.");
            String id = normalizeStatId(entry.getKey());
            if (validated.putIfAbsent(id, entry.getValue()) != null) throw new IllegalArgumentException("Duplicate persisted stat: " + id);
        }
        additionalBaseStats.clear();
        additionalBaseStats.putAll(validated);
        clampVitals();
    }

    /** Restore mutable HP/Mana resources from persistence, clamped against the newly rebuilt derived maxima. */
    public void restoreVitals(float savedHealth, float savedMana) {
        if (!Float.isFinite(savedHealth) || savedHealth < 0 || !Float.isFinite(savedMana) || savedMana < 0)
            throw new IllegalArgumentException("Persisted health/mana must be finite and non-negative.");
        health = Math.min(savedHealth, maxHealth());
        mana = Math.min(savedMana, maxMana());
    }

    /** Replace one effect source atomically, e.g. inventory_equipment, pet:fox, area:skyland, buff:elixir. */
    public void setModifierSource(String sourceId, Collection<StatModifier> modifiers) {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("Modifier source ID cannot be blank.");
        Objects.requireNonNull(modifiers, "modifiers");
        List<StatModifier> copy = List.copyOf(modifiers);
        for (StatModifier modifier : copy) Objects.requireNonNull(modifier, "modifier");
        if (copy.isEmpty()) modifierSources.remove(sourceId);
        else modifierSources.put(sourceId, copy);
        clampVitals();
    }

    public void removeModifierSource(String sourceId) {
        if (sourceId != null && modifierSources.remove(sourceId) != null) clampVitals();
    }

    public Map<String, List<StatModifier>> modifierSources() { return Map.copyOf(modifierSources); }

    /** Defense uses diminishing returns: effective damage = raw * 100 / (100 + defense). */
    public float takeDamage(float rawDamage) {
        if (!Float.isFinite(rawDamage) || rawDamage < 0) throw new IllegalArgumentException("Damage must be finite and non-negative.");
        if (!alive() || rawDamage == 0) return 0;
        float applied = rawDamage * (100.0f / (100.0f + defense()));
        applied = Math.min(health, applied);
        health = Math.max(0, health - applied);
        return applied;
    }

    public float heal(float amount) {
        if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Heal must be finite and non-negative.");
        float before = health;
        health = Math.min(maxHealth(), health + amount);
        return health - before;
    }

    public boolean spendMana(float amount) {
        if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Mana cost must be finite and non-negative.");
        if (mana + 1.0e-5f < amount || !alive()) return false;
        mana = Math.max(0, mana - amount);
        return true;
    }

    public void restoreMana(float amount) {
        if (!Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Mana restore must be finite and non-negative.");
        mana = Math.min(maxMana(), mana + amount);
    }

    public void setActiveBuffs(StatModifiers buffs) {
        activeBuffs = Objects.requireNonNull(buffs, "buffs");
        clampVitals();
    }
    public StatModifiers activeBuffs() { return activeBuffs; }

    public float attackDamage() {
        float weaponDamage = stat("weapon_damage");
        if (weaponDamage <= 0.0f) weaponDamage = equipment.weaponDamage();
        return Math.max(1.0f, (weaponDamage + strength() * 0.35f) * stat("damage_multiplier"));
    }

    public void tick(float deltaSeconds) {
        float dt = Math.max(0, Math.min(deltaSeconds, 0.25f));
        if (alive()) {
            restoreMana(stat("mana_regen") * dt);
            float healthRegen = stat("health_regen");
            if (healthRegen > 0) heal(healthRegen * dt);
        }
        clampVitals();
    }

    public void respawn() { health = maxHealth(); mana = maxMana(); }

    private float baseValue(String id) {
        return switch (id) {
            case "max_health" -> additionalBaseStats.getOrDefault(id, baseMaxHealth);
            case "max_mana" -> additionalBaseStats.getOrDefault(id, baseMaxMana);
            case "defense" -> additionalBaseStats.getOrDefault(id, baseDefense);
            case "strength" -> additionalBaseStats.getOrDefault(id, baseStrength);
            case "intelligence" -> additionalBaseStats.getOrDefault(id, baseIntelligence);
            case "movement_speed", "damage_multiplier", "attack_speed" -> additionalBaseStats.getOrDefault(id, definitions.find(id).map(StatDefinition::defaultBase).orElse(1.0f));
            default -> additionalBaseStats.getOrDefault(id, definitions.find(id).map(StatDefinition::defaultBase).orElse(0.0f));
        };
    }

    private List<StatModifier> allModifiers() {
        ArrayList<StatModifier> all = new ArrayList<>();
        for (List<StatModifier> source : modifierSources.values()) all.addAll(source);
        StatModifiers legacy = equipment.totalModifiers();
        addLegacy(all, "legacy_equipment", legacy);
        addLegacy(all, "legacy_active_buffs", activeBuffs);
        // These are intentionally generic: additional sources never need PlayerStats fields.
        return all;
    }

    private static void addLegacy(List<StatModifier> out, String source, StatModifiers mods) {
        addFlat(out, source, "max_health", mods.maxHealth());
        addFlat(out, source, "max_mana", mods.maxMana());
        addFlat(out, source, "defense", mods.defense());
        addFlat(out, source, "strength", mods.strength());
        addFlat(out, source, "intelligence", mods.intelligence());
        if (mods.speedPercent() != 0) out.add(new StatModifier(source, "movement_speed", ModifierOperation.PERCENT, mods.speedPercent()));
        if (mods.damagePercent() != 0) out.add(new StatModifier(source, "damage_multiplier", ModifierOperation.PERCENT, mods.damagePercent()));
    }
    private static void addFlat(List<StatModifier> out, String source, String id, float value) {
        if (value != 0) out.add(new StatModifier(source, id, ModifierOperation.FLAT, value));
    }
    private void clampVitals() {
        health = Math.min(health, maxHealth());
        mana = Math.min(mana, maxMana());
    }
    private static String normalizeStatId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Stat ID cannot be blank.");
        return id.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
