package dev.itzsiden.skyvoxel.engine.gameplay;

/** Additive RPG stat modifiers. speedPercent and damagePercent use decimal fractions (0.05 = +5%). */
public record StatModifiers(float maxHealth, float maxMana, float defense,
                            float strength, float intelligence,
                            float speedPercent, float damagePercent) {
    public static final StatModifiers ZERO = new StatModifiers(0, 0, 0, 0, 0, 0, 0);

    public StatModifiers {
        if (!Float.isFinite(maxHealth) || !Float.isFinite(maxMana) || !Float.isFinite(defense)
            || !Float.isFinite(strength) || !Float.isFinite(intelligence)
            || !Float.isFinite(speedPercent) || !Float.isFinite(damagePercent)) {
            throw new IllegalArgumentException("Stat modifiers must be finite.");
        }
    }

    public StatModifiers plus(StatModifiers other) {
        return new StatModifiers(maxHealth + other.maxHealth, maxMana + other.maxMana,
            defense + other.defense, strength + other.strength, intelligence + other.intelligence,
            speedPercent + other.speedPercent, damagePercent + other.damagePercent);
    }
}
