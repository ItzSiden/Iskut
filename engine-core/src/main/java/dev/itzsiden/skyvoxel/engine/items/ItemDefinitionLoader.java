package dev.itzsiden.skyvoxel.engine.items;

import dev.itzsiden.skyvoxel.engine.stats.ModifierOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Loads individual UTF-8 .item files. One item per file keeps merges/version control manageable at scale. */
public final class ItemDefinitionLoader {
    private ItemDefinitionLoader() {}

    public static ItemDefinitionRegistry loadDirectory(Path root) throws IOException {
        if (!Files.isDirectory(root)) throw new IOException("Item definitions directory does not exist: " + root.toAbsolutePath());
        ItemDefinitionRegistry registry = new ItemDefinitionRegistry();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".item"))
                    .sorted().toList()) {
                try {
                    registry.register(load(file));
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid item definition " + file + ": " + exception.getMessage(), exception);
                }
            }
        }
        if (registry.size() == 0) throw new IOException("No .item definitions found under " + root.toAbsolutePath());
        return registry;
    }

    public static ItemDefinition load(Path file) throws IOException {
        String id = null, name = null, description = "", category = null, rarity = "COMMON", icon = "?";
        String equipmentSlot = "NONE";
        int maxStack = 64;
        float baseDamage = 0;
        long sellPrice = 0;
        List<ItemStatModifier> modifiers = new ArrayList<>();
        List<ItemUseEffect> useEffects = new ArrayList<>();
        Set<String> tags = new LinkedHashSet<>();
        int lineNo = 0;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int equals = line.indexOf('=');
            if (equals <= 0) throw new IOException("Expected key=value at " + file + ":" + lineNo);
            String key = line.substring(0, equals).strip().toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1).strip();
            try {
                switch (key) {
                    case "id" -> id = value;
                    case "name" -> name = value;
                    case "description" -> description = value;
                    case "category" -> category = value;
                    case "rarity" -> rarity = value;
                    case "icon" -> icon = value;
                    case "max_stack" -> maxStack = Integer.parseInt(value);
                    case "equipment_slot" -> equipmentSlot = value;
                    case "base_damage" -> baseDamage = Float.parseFloat(value);
                    case "sell_price" -> sellPrice = Long.parseLong(value);
                    case "tag" -> { if (!value.isBlank()) tags.add(value.toLowerCase(Locale.ROOT)); }
                    case "modifier" -> modifiers.add(parseModifier(value));
                    case "use_effect" -> useEffects.add(ItemUseEffect.parse(value));
                    default -> throw new IOException("Unknown field '" + key + "' at " + file + ":" + lineNo);
                }
            } catch (IllegalArgumentException exception) {
                throw new IOException("Bad value at " + file + ":" + lineNo + " (" + key + "): " + exception.getMessage(), exception);
            }
        }
        try {
            return new ItemDefinition(id, name, description, category, ItemRarity.parse(rarity), maxStack,
                icon, ItemEquipmentSlot.parse(equipmentSlot), baseDamage, sellPrice, modifiers, tags, useEffects);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid definition in " + file + ": " + exception.getMessage(), exception);
        }
    }

    private static ItemStatModifier parseModifier(String encoded) {
        String[] parts = encoded.split("\\|", -1);
        if (parts.length != 3) throw new IllegalArgumentException("modifier format is stat_id|FLAT/PERCENT/MULTIPLIER|amount");
        return new ItemStatModifier(parts[0], ModifierOperation.valueOf(parts[1].strip().toUpperCase(Locale.ROOT)), Float.parseFloat(parts[2].strip()));
    }
}
