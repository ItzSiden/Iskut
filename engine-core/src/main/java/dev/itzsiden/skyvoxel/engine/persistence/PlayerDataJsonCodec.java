package dev.itzsiden.skyvoxel.engine.persistence;

import dev.itzsiden.skyvoxel.engine.items.ItemEquipmentSlot;
import dev.itzsiden.skyvoxel.engine.items.InventorySnapshot;
import dev.itzsiden.skyvoxel.engine.items.ItemStatModifier;
import dev.itzsiden.skyvoxel.engine.stats.ModifierOperation;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small dependency-free JSON codec for the stable player-save schema. */
final class PlayerDataJsonCodec {
    private PlayerDataJsonCodec() {}

    static String encode(PlayerDataSnapshot data) {
        StringBuilder out = new StringBuilder(8192);
        out.append("{\n  \"schemaVersion\": ").append(data.schemaVersion())
            .append(",\n  \"playerId\": ").append(string(data.playerId()))
            .append(",\n  \"savedAtEpochMillis\": ").append(data.savedAtEpochMillis())
            .append(",\n  \"coins\": ").append(data.coins())
            .append(",\n  \"baseStats\": {");
        int statIndex = 0;
        for (Map.Entry<String, Float> entry : data.baseStats().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (statIndex++ > 0) out.append(',');
            out.append("\n    ").append(string(entry.getKey())).append(": ").append(number(entry.getValue()));
        }
        if (statIndex > 0) out.append('\n').append("  ");
        out.append("},\n  \"resources\": {\"health\": ").append(number(data.health()))
            .append(", \"mana\": ").append(number(data.mana())).append("},\n  \"inventory\": {")
            .append("\n    \"selectedHotbarIndex\": ").append(data.inventory().selectedHotbarIndex())
            .append(",\n    \"storage\": [");
        for (int i = 0; i < data.inventory().storage().size(); i++) {
            if (i > 0) out.append(',');
            if (i % 9 == 0) out.append("\n      ");
            out.append(encodeStack(data.inventory().storage().get(i)));
        }
        out.append("\n    ],\n    \"equipment\": {");
        int e = 0;
        for (Map.Entry<String, InventorySnapshot.StackData> entry : data.inventory().equipment().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (e++ > 0) out.append(',');
            out.append("\n      ").append(string(entry.getKey())).append(": ").append(encodeStack(entry.getValue()));
        }
        out.append("\n    },\n    \"accessories\": [");
        for (int i = 0; i < data.inventory().accessories().size(); i++) {
            if (i > 0) out.append(", ");
            out.append(encodeStack(data.inventory().accessories().get(i)));
        }
        out.append("]\n  }\n}\n");
        return out.toString();
    }

    static PlayerDataSnapshot decode(String json) {
        Object parsed = new Parser(json).parse();
        Map<String, Object> root = object(parsed, "root");
        int schemaVersion = intValue(required(root, "schemaVersion"), "schemaVersion");
        String playerId = stringValue(required(root, "playerId"), "playerId");
        long savedAt = longValue(required(root, "savedAtEpochMillis"), "savedAtEpochMillis");
        long coins = longValue(required(root, "coins"), "coins");
        Map<String, Object> rawBaseStats = object(required(root, "baseStats"), "baseStats");
        Map<String, Float> baseStats = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawBaseStats.entrySet()) {
            baseStats.put(entry.getKey(), floatValue(entry.getValue(), "baseStats." + entry.getKey()));
        }
        Map<String, Object> resources = object(required(root, "resources"), "resources");
        float health = floatValue(required(resources, "health"), "resources.health");
        float mana = floatValue(required(resources, "mana"), "resources.mana");
        Map<String, Object> rawInventory = object(required(root, "inventory"), "inventory");
        int selected = intValue(required(rawInventory, "selectedHotbarIndex"), "inventory.selectedHotbarIndex");
        List<Object> rawStorage = array(required(rawInventory, "storage"), "inventory.storage");
        List<InventorySnapshot.StackData> storage = new ArrayList<>(rawStorage.size());
        for (int i = 0; i < rawStorage.size(); i++) storage.add(decodeStack(rawStorage.get(i), "inventory.storage[" + i + "]"));
        Map<String, Object> rawEquipment = object(required(rawInventory, "equipment"), "inventory.equipment");
        Map<String, InventorySnapshot.StackData> equipment = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawEquipment.entrySet()) {
            String slot = entry.getKey();
            ItemEquipmentSlot parsedSlot;
            try { parsedSlot = ItemEquipmentSlot.valueOf(slot); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown equipment slot in save: " + slot, invalid); }
            if (parsedSlot == ItemEquipmentSlot.NONE || parsedSlot == ItemEquipmentSlot.ACCESSORY)
                throw new IllegalArgumentException("Invalid named equipment slot in save: " + slot);
            equipment.put(slot, decodeStack(entry.getValue(), "inventory.equipment." + slot));
        }
        for (ItemEquipmentSlot slot : ItemEquipmentSlot.values()) {
            if (slot == ItemEquipmentSlot.NONE || slot == ItemEquipmentSlot.ACCESSORY) continue;
            if (!equipment.containsKey(slot.name())) throw new IllegalArgumentException("Missing equipment slot in save: " + slot);
        }
        List<Object> rawAccessories = array(required(rawInventory, "accessories"), "inventory.accessories");
        List<InventorySnapshot.StackData> accessories = new ArrayList<>(rawAccessories.size());
        for (int i = 0; i < rawAccessories.size(); i++) accessories.add(decodeStack(rawAccessories.get(i), "inventory.accessories[" + i + "]"));
        InventorySnapshot inventory = new InventorySnapshot(storage, equipment, accessories, selected);
        return new PlayerDataSnapshot(schemaVersion, playerId, savedAt, coins, baseStats, health, mana, inventory);
    }

    private static String encodeStack(InventorySnapshot.StackData stack) {
        StringBuilder out = new StringBuilder(80).append("{\"itemId\": ").append(string(stack.itemId()))
            .append(", \"amount\": ").append(stack.amount());
        if (!stack.instanceId().isEmpty()) out.append(", \"instanceId\": ").append(string(stack.instanceId()));
        if (!stack.customData().isEmpty()) {
            out.append(", \"customData\": {");
            int index = 0;
            for (Map.Entry<String, String> entry : stack.customData().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (index++ > 0) out.append(", ");
                out.append(string(entry.getKey())).append(": ").append(string(entry.getValue()));
            }
            out.append('}');
        }
        if (!stack.instanceModifiers().isEmpty()) {
            out.append(", \"instanceModifiers\": [");
            for (int i = 0; i < stack.instanceModifiers().size(); i++) {
                if (i > 0) out.append(", ");
                ItemStatModifier m = stack.instanceModifiers().get(i);
                out.append("{\"statId\": ").append(string(m.statId()))
                    .append(", \"operation\": ").append(string(m.operation().name()))
                    .append(", \"amount\": ").append(number(m.amount())).append('}');
            }
            out.append(']');
        }
        return out.append('}').toString();
    }

    private static InventorySnapshot.StackData decodeStack(Object value, String where) {
        Map<String, Object> map = object(value, where);
        String itemId = stringValue(required(map, "itemId"), where + ".itemId");
        int amount = intValue(required(map, "amount"), where + ".amount");
        String instanceId = map.containsKey("instanceId") ? stringValue(map.get("instanceId"), where + ".instanceId") : "";
        Map<String, String> customData = new LinkedHashMap<>();
        if (map.containsKey("customData")) {
            for (Map.Entry<String, Object> entry : object(map.get("customData"), where + ".customData").entrySet()) {
                customData.put(entry.getKey(), stringValue(entry.getValue(), where + ".customData." + entry.getKey()));
            }
        }
        List<ItemStatModifier> modifiers = new ArrayList<>();
        if (map.containsKey("instanceModifiers")) {
            List<Object> items = array(map.get("instanceModifiers"), where + ".instanceModifiers");
            for (Object item : items) {
                Map<String, Object> m = object(item, where + ".instanceModifiers[]");
                modifiers.add(new ItemStatModifier(stringValue(required(m, "statId"), "statId"),
                    ModifierOperation.valueOf(stringValue(required(m, "operation"), "operation")),
                    floatValue(required(m, "amount"), "amount")));
            }
        }
        return new InventorySnapshot.StackData(itemId, amount, instanceId, customData, modifiers);
    }

    private static String number(float f) {
        if (!Float.isFinite(f)) throw new IllegalArgumentException("Non-finite float cannot be encoded as JSON.");
        return Float.toString(f);
    }
    private static String string(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
    private static Object required(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) throw new IllegalArgumentException("Missing JSON field: " + key);
        return map.get(key);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value, String where) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(where + " must be an object.");
        for (Object key : map.keySet()) if (!(key instanceof String)) throw new IllegalArgumentException(where + " has a non-string key.");
        return (Map<String, Object>) map;
    }
    @SuppressWarnings("unchecked") private static List<Object> array(Object value, String where) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(where + " must be an array.");
        return (List<Object>) list;
    }
    private static String stringValue(Object value, String where) {
        if (!(value instanceof String s)) throw new IllegalArgumentException(where + " must be a string.");
        return s;
    }
    private static double numeric(Object value, String where) {
        if (!(value instanceof Number n)) throw new IllegalArgumentException(where + " must be a number.");
        double d = n.doubleValue();
        if (!Double.isFinite(d)) throw new IllegalArgumentException(where + " must be finite.");
        return d;
    }
    private static int intValue(Object value, String where) {
        double d = numeric(value, where);
        if (d != Math.rint(d) || d < Integer.MIN_VALUE || d > Integer.MAX_VALUE) throw new IllegalArgumentException(where + " must be an integer.");
        return (int) d;
    }
    private static long longValue(Object value, String where) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof BigInteger big) {
            try { return big.longValueExact(); }
            catch (ArithmeticException outsideRange) { throw new IllegalArgumentException(where + " is outside signed 64-bit range.", outsideRange); }
        }
        double d = numeric(value, where);
        if (d != Math.rint(d) || d < Long.MIN_VALUE || d >= 0x1.0p63 || d < -0x1.0p63)
            throw new IllegalArgumentException(where + " must be an integer in signed 64-bit range.");
        return (long) d;
    }
    private static float floatValue(Object value, String where) {
        double d = numeric(value, where);
        if (d < -Float.MAX_VALUE || d > Float.MAX_VALUE) throw new IllegalArgumentException(where + " is out of float range.");
        return (float) d;
    }

    /** Strict JSON parser; keeps persistence dependency-free and rejects trailing garbage/duplicate keys. */
    private static final class Parser {
        private final String input;
        private int index;
        Parser(String input) { this.input = input == null ? "" : input; }
        Object parse() {
            skipWhitespace(); Object value = value(); skipWhitespace();
            if (index != input.length()) fail("Trailing content");
            return value;
        }
        private Object value() {
            skipWhitespace();
            if (index >= input.length()) return fail("Unexpected end of input");
            return switch (input.charAt(index)) {
                case '{' -> object(); case '[' -> array(); case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE); case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null); default -> number();
            };
        }
        private Map<String, Object> object() {
            index++; skipWhitespace(); Map<String, Object> result = new LinkedHashMap<>();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace(); if (index >= input.length() || input.charAt(index) != '"') fail("Object key must be a string");
                String key = string(); skipWhitespace(); expect(':'); Object child = value();
                if (result.containsKey(key)) fail("Duplicate object key '" + key + "'");
                result.put(key, child); skipWhitespace();
                if (consume('}')) return result; expect(',');
            }
        }
        private List<Object> array() {
            index++; skipWhitespace(); List<Object> result = new ArrayList<>();
            if (consume(']')) return result;
            while (true) { result.add(value()); skipWhitespace(); if (consume(']')) return result; expect(','); }
        }
        private String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == '"') return out.toString();
                if (c < 0x20) fail("Control character in string");
                if (c != '\\') { out.append(c); continue; }
                if (index >= input.length()) fail("Incomplete escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b'); case 'f' -> out.append('\f'); case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r'); case 't' -> out.append('\t'); case 'u' -> {
                        if (index + 4 > input.length()) fail("Incomplete unicode escape");
                        try { out.append((char) Integer.parseInt(input.substring(index, index + 4), 16)); }
                        catch (NumberFormatException e) { fail("Invalid unicode escape"); }
                        index += 4;
                    }
                    default -> fail("Invalid escape character");
                }
            }
            return fail("Unterminated string");
        }
        private Object number() {
            int start = index;
            if (consume('-')) { /* sign */ }
            if (consume('0')) { /* leading zero */ } else {
                if (index >= input.length() || !digit(input.charAt(index))) return fail("Invalid number");
                while (index < input.length() && digit(input.charAt(index))) index++;
            }
            if (consume('.')) { if (index >= input.length() || !digit(input.charAt(index))) return fail("Invalid decimal"); while (index < input.length() && digit(input.charAt(index))) index++; }
            if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                index++; if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                if (index >= input.length() || !digit(input.charAt(index))) return fail("Invalid exponent");
                while (index < input.length() && digit(input.charAt(index))) index++;
            }
            String token = input.substring(start, index);
            try {
                if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
                    try { return Long.parseLong(token); }
                    catch (NumberFormatException outsideLong) { return new BigInteger(token); }
                }
                double parsed = Double.parseDouble(token);
                if (!Double.isFinite(parsed)) return fail("Non-finite number");
                return parsed;
            } catch (NumberFormatException e) { return fail("Invalid number"); }
        }
        private Object literal(String text, Object value) {
            if (!input.startsWith(text, index)) return fail("Invalid literal");
            index += text.length(); return value;
        }
        private void skipWhitespace() { while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++; }
        private boolean consume(char expected) { if (index < input.length() && input.charAt(index) == expected) { index++; return true; } return false; }
        private void expect(char expected) { if (!consume(expected)) fail("Expected '" + expected + "'"); }
        private boolean digit(char c) { return c >= '0' && c <= '9'; }
        private <T> T fail(String message) { throw new IllegalArgumentException("Invalid player JSON at character " + index + ": " + message); }
    }
}
