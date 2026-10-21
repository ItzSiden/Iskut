package dev.itzsiden.skyvoxel.engine.items;

import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Executes registered built-in use-effect IDs against runtime services, consuming only after an effect succeeds. */
public final class ItemUseService {
    private ItemUseService() {}

    public static ItemUseResult useSelected(InventoryModel inventory, PlayerStats stats, ItemDefinitionRegistry catalog) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(catalog, "catalog");
        int storageIndex = InventoryModel.HOTBAR_START + inventory.selectedHotbarIndex();
        ItemStack stack = inventory.storage(storageIndex);
        if (stack == null) return ItemUseResult.failed("Selected hotbar slot is empty.");
        ItemDefinition definition = catalog.require(stack.itemId());
        if (definition.useEffects().isEmpty()) return ItemUseResult.failed(definition.displayName() + " cannot be used that way.");
        if (!stats.alive()) return ItemUseResult.failed("You cannot use items while defeated.");

        List<String> applied = new ArrayList<>();
        boolean unsupported = false;
        for (ItemUseEffect effect : definition.useEffects()) {
            String type = effect.type().toLowerCase(Locale.ROOT);
            if (type.equals("heal") || type.equals("restore_health")) {
                float actual = stats.heal(effect.amount());
                if (actual > 0.001f) applied.add("+" + Math.round(actual) + " HP");
            } else if (type.equals("restore_mana") || type.equals("mana")) {
                float before = stats.mana();
                stats.restoreMana(effect.amount());
                float actual = stats.mana() - before;
                if (actual > 0.001f) applied.add("+" + Math.round(actual) + " Mana");
            } else {
                unsupported = true;
            }
        }
        if (applied.isEmpty()) {
            return ItemUseResult.failed(unsupported
                ? "This item's effect is not available yet."
                : "Your resources are already full.");
        }
        inventory.takeStorage(storageIndex, 1, catalog);
        return ItemUseResult.success("Used " + definition.displayName() + ": " + String.join("  ·  ", applied));
    }
}
