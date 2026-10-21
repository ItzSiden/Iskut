package dev.itzsiden.skyvoxel.engine.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** O(1) ID lookup and deterministic sorted views for 1,000+ item catalogs. */
public final class ItemDefinitionRegistry {
    private final Map<String, ItemDefinition> byId = new LinkedHashMap<>();

    public ItemDefinitionRegistry register(ItemDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String id = normalize(definition.id());
        if (byId.putIfAbsent(id, definition) != null) throw new IllegalArgumentException("Duplicate item ID: " + id);
        return this;
    }
    public Optional<ItemDefinition> find(String id) { return Optional.ofNullable(byId.get(normalize(id))); }
    public ItemDefinition require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown item ID: " + id));
    }
    public int size() { return byId.size(); }
    public Collection<ItemDefinition> all() { return List.copyOf(byId.values()); }
    public List<ItemDefinition> sorted() {
        ArrayList<ItemDefinition> result = new ArrayList<>(byId.values());
        result.sort(Comparator.comparing(ItemDefinition::categoryId).thenComparing(ItemDefinition::displayName).thenComparing(ItemDefinition::id));
        return List.copyOf(result);
    }
    public List<ItemDefinition> category(String categoryId) {
        String key = normalize(categoryId);
        return sorted().stream().filter(item -> item.categoryId().equals(key)).toList();
    }
    private static String normalize(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Item ID cannot be blank.");
        return id.strip().toLowerCase(Locale.ROOT);
    }
}
