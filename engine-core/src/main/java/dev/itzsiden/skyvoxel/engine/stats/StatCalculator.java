package dev.itzsiden.skyvoxel.engine.stats;

import java.util.Collection;

/** Pure, deterministic modifier calculator. Can be reused unchanged on a future authoritative server. */
public final class StatCalculator {
    private StatCalculator() {}

    public static float calculate(String statId, float baseValue, Collection<StatModifier> modifiers,
                                  StatDefinitionRegistry registry) {
        if (!Float.isFinite(baseValue)) throw new IllegalArgumentException("Base stat must be finite.");
        float flat = 0.0f;
        float percent = 0.0f;
        float multiplier = 1.0f;
        String wanted = statId.toLowerCase(java.util.Locale.ROOT);
        for (StatModifier modifier : modifiers) {
            if (!modifier.statId().equals(wanted)) continue;
            switch (modifier.operation()) {
                case FLAT -> flat += modifier.amount();
                case PERCENT -> percent += modifier.amount();
                case MULTIPLIER -> multiplier *= modifier.amount();
            }
        }
        float result = (baseValue + flat) * Math.max(0.0f, 1.0f + percent) * multiplier;
        if (!Float.isFinite(result)) result = Float.MAX_VALUE;
        var definition = registry.find(wanted);
        if (definition.isPresent()) {
            StatDefinition bounds = definition.get();
            result = Math.max(bounds.minimum(), Math.min(bounds.maximum(), result));
        }
        return result;
    }
}
