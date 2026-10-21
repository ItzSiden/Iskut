package dev.itzsiden.skyvoxel.client.entity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader used for data-driven Blockbench exports without a runtime dependency. */
final class MiniJson {
    private final String source;
    private int cursor;

    private MiniJson(String source) { this.source = source; }

    static Object read(Path path) throws IOException {
        String input = Files.readString(path, StandardCharsets.UTF_8);
        try {
            MiniJson parser = new MiniJson(input);
            Object value = parser.value();
            parser.space();
            if (parser.cursor != input.length()) throw parser.error("Unexpected trailing content");
            return value;
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid JSON in " + path + ": " + ex.getMessage(), ex);
        }
    }

    private Object value() {
        space();
        if (cursor >= source.length()) throw error("Unexpected end of input");
        return switch (source.charAt(cursor)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        cursor++;
        space();
        Map<String, Object> map = new LinkedHashMap<>();
        if (consume('}')) return map;
        while (true) {
            space();
            if (cursor >= source.length() || source.charAt(cursor) != '"') throw error("Expected object key");
            String key = string();
            space(); expect(':');
            if (map.containsKey(key)) throw error("Duplicate object key '" + key + "'");
            map.put(key, value());
            space();
            if (consume('}')) return map;
            expect(',');
        }
    }


    private List<Object> array() {
        cursor++;
        space();
        List<Object> list = new ArrayList<>();
        if (consume(']')) return list;
        while (true) {
            list.add(value());
            space();
            if (consume(']')) return list;
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (cursor < source.length()) {
            char c = source.charAt(cursor++);
            if (c == '"') return result.toString();
            if (c < 0x20) throw error("Control character in string");
            if (c != '\\') { result.append(c); continue; }
            if (cursor >= source.length()) throw error("Incomplete escape sequence");
            char e = source.charAt(cursor++);
            switch (e) {
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (cursor + 4 > source.length()) throw error("Incomplete unicode escape");
                    try { result.append((char) Integer.parseInt(source.substring(cursor, cursor + 4), 16)); }
                    catch (NumberFormatException ex) { throw error("Invalid unicode escape"); }
                    cursor += 4;
                }
                default -> throw error("Unknown escape sequence \\" + e + "'");
            }
        }
        throw error("Unterminated string");
    }

    private Object number() {
        int start = cursor;
        if (cursor < source.length() && source.charAt(cursor) == '-') cursor++;
        if (cursor >= source.length()) throw error("Incomplete number");
        if (source.charAt(cursor) == '0') cursor++;
        else {
            if (!digit(source.charAt(cursor))) throw error("Expected a value");
            while (cursor < source.length() && digit(source.charAt(cursor))) cursor++;
        }
        if (cursor < source.length() && source.charAt(cursor) == '.') {
            cursor++;
            int decimalStart = cursor;
            while (cursor < source.length() && digit(source.charAt(cursor))) cursor++;
            if (decimalStart == cursor) throw error("Fraction has no digits");
        }
        if (cursor < source.length() && (source.charAt(cursor) == 'e' || source.charAt(cursor) == 'E')) {
            cursor++;
            if (cursor < source.length() && (source.charAt(cursor) == '+' || source.charAt(cursor) == '-')) cursor++;
            int exponentStart = cursor;
            while (cursor < source.length() && digit(source.charAt(cursor))) cursor++;
            if (exponentStart == cursor) throw error("Exponent has no digits");
        }
        try { return Double.parseDouble(source.substring(start, cursor)); }
        catch (NumberFormatException ex) { throw error("Invalid number"); }
    }

    private Object literal(String literal, Object value) {
        if (!source.startsWith(literal, cursor)) throw error("Invalid literal");
        cursor += literal.length();
        return value;
    }

    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private void space() { while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++; }
    private boolean consume(char c) { if (cursor < source.length() && source.charAt(cursor) == c) { cursor++; return true; } return false; }
    private void expect(char c) { space(); if (!consume(c)) throw error("Expected '" + c + "'"); }
    private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at character " + cursor); }
}
