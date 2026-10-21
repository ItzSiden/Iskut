package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Loader for authored .vmodel data. Geometry stays reusable while instances carry separate transforms. */
public final class EntityModelLoader {
    private static final Map<String, Material> MATERIALS = Map.ofEntries(
        Map.entry("navy", new Material("entity_navy", 0.12f, 0.22f, 0.42f)),
        Map.entry("blue", new Material("entity_blue", 0.18f, 0.42f, 0.82f)),
        Map.entry("teal", new Material("entity_teal", 0.10f, 0.62f, 0.62f)),
        Map.entry("skin", new Material("entity_skin", 0.91f, 0.68f, 0.49f)),
        Map.entry("dark", new Material("entity_dark", 0.12f, 0.14f, 0.22f)),
        Map.entry("gold", new Material("entity_gold", 0.98f, 0.66f, 0.17f)),
        Map.entry("violet", new Material("entity_violet", 0.47f, 0.26f, 0.88f)),
        Map.entry("cyan", new Material("entity_cyan", 0.24f, 0.88f, 0.98f)),
        Map.entry("white", new Material("entity_white", 0.90f, 0.95f, 1.0f)),
        Map.entry("red", new Material("entity_red", 0.86f, 0.18f, 0.20f)),
        Map.entry("leaf", new Material("entity_leaf", 0.20f, 0.66f, 0.39f)),
        Map.entry("brown", new Material("entity_brown", 0.39f, 0.24f, 0.15f)),
        Map.entry("black", new Material("entity_black", 0.055f, 0.065f, 0.085f)),
        Map.entry("gray", new Material("entity_gray", 0.42f, 0.46f, 0.51f)),
        Map.entry("grey", new Material("entity_grey", 0.42f, 0.46f, 0.51f)),
        Map.entry("silver", new Material("entity_silver", 0.72f, 0.78f, 0.84f)),
        Map.entry("iron", new Material("entity_iron", 0.54f, 0.60f, 0.67f)),
        Map.entry("steel", new Material("entity_steel", 0.36f, 0.44f, 0.54f)),
        Map.entry("orange", new Material("entity_orange", 0.96f, 0.39f, 0.12f)),
        Map.entry("pink", new Material("entity_pink", 0.95f, 0.38f, 0.62f)),
        Map.entry("magenta", new Material("entity_magenta", 0.77f, 0.20f, 0.65f)),
        Map.entry("lime", new Material("entity_lime", 0.58f, 0.88f, 0.22f)),
        Map.entry("emerald", new Material("entity_emerald", 0.08f, 0.58f, 0.35f)),
        Map.entry("ice", new Material("entity_ice", 0.68f, 0.90f, 0.98f)),
        Map.entry("wood", new Material("entity_wood", 0.48f, 0.29f, 0.15f)),
        Map.entry("leather", new Material("entity_leather", 0.28f, 0.16f, 0.12f)),
        Map.entry("copper", new Material("entity_copper", 0.72f, 0.34f, 0.19f)),
        Map.entry("bronze", new Material("entity_bronze", 0.62f, 0.39f, 0.20f)),
        Map.entry("stone", new Material("entity_stone", 0.43f, 0.46f, 0.49f)),
        Map.entry("cream", new Material("entity_cream", 0.93f, 0.84f, 0.63f)),
        Map.entry("yellow", new Material("entity_yellow", 0.98f, 0.86f, 0.23f))
    );

    private final Mesh cubeMesh;
    public EntityModelLoader(Mesh cubeMesh) { this.cubeMesh = java.util.Objects.requireNonNull(cubeMesh, "cubeMesh"); }

    /** Loads legacy flat-colour v1 or self-contained pixel-textured v2 cuboid models. */
    public EntityModel load(Path path) throws IOException {
        String id = null;
        float modelHeight = 1.8f;
        int version = 0;
        var partSpecs = new ArrayList<PartSpec>();
        var partsById = new LinkedHashMap<String, PartSpec>();
        var pixelTextures = new LinkedHashMap<String, VModelTextureAtlas.PixelTexture>();
        PixelTextureBuilder activeTexture = null;
        boolean header = false;
        int lineNumber = 0;
        var lines = Files.readAllLines(path);
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            lineNumber = i + 1;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            try {
                if (activeTexture != null) {
                    switch (t[0]) {
                        case "PALETTE" -> {
                            if (t.length != 4 || !normalize(t[1]).equals(activeTexture.id))
                                throw new IllegalArgumentException("Expected PALETTE " + activeTexture.id + " <symbol> <#RRGGBB[AA]>.");
                            if (t[2].length() != 1 || Character.isWhitespace(t[2].charAt(0)))
                                throw new IllegalArgumentException("Palette symbols must be exactly one non-whitespace character.");
                            char symbol = t[2].charAt(0);
                            if (activeTexture.palette.putIfAbsent(symbol, parsePixelColor(t[3])) != null)
                                throw new IllegalArgumentException("Duplicate palette symbol '" + symbol + "'.");
                        }
                        case "PIXEL" -> {
                            if (t.length != 4 || !normalize(t[1]).equals(activeTexture.id))
                                throw new IllegalArgumentException("Expected PIXEL " + activeTexture.id + " <row-index> <pixel-symbols>.");
                            int row = Integer.parseInt(t[2]);
                            if (row < 0 || row >= activeTexture.height) throw new IllegalArgumentException("Texture row index is out of bounds: " + row);
                            if (t[3].length() != activeTexture.width)
                                throw new IllegalArgumentException("Pixel row " + row + " must contain exactly " + activeTexture.width + " symbols.");
                            if (activeTexture.rows[row] != null) throw new IllegalArgumentException("Duplicate pixel row " + row + ".");
                            activeTexture.rows[row] = t[3];
                        }
                        case "END_TEXTURE" -> {
                            require(t, 1);
                            VModelTextureAtlas.PixelTexture built = activeTexture.build();
                            if (pixelTextures.putIfAbsent(built.id(), built) != null)
                                throw new IllegalArgumentException("Duplicate texture id '" + built.id() + "'.");
                            activeTexture = null;
                        }
                        default -> throw new IllegalArgumentException("Only PALETTE, PIXEL and END_TEXTURE are allowed inside a TEXTURE block.");
                    }
                    continue;
                }

                switch (t[0]) {
                    case "SKYVOXEL_ENTITY_MODEL", "ISKUT_ITEM_MODEL", "ISKUT_MODEL", "SKYVOXEL_ITEM_MODEL" -> {
                        if (t.length != 2 || !(t[1].equals("1") || t[1].equals("2")))
                            throw new IllegalArgumentException("Expected a vmodel header version 1 or 2.");
                        version = Integer.parseInt(t[1]);
                        header = true;
                    }
                    case "NAME" -> { require(t, 2); id = t[1]; }
                    case "HEIGHT" -> { require(t, 2); modelHeight = positiveFloat(t[1]); }
                    case "TEXTURE" -> {
                        if (version < 2) throw new IllegalArgumentException("Embedded pixel textures require vmodel version 2.");
                        require(t, 4);
                        String textureId = normalize(t[1]);
                        if (textureId.isBlank()) throw new IllegalArgumentException("Texture id cannot be blank.");
                        int w = Integer.parseInt(t[2]), h = Integer.parseInt(t[3]);
                        activeTexture = new PixelTextureBuilder(textureId, w, h);
                    }
                    case "PART" -> {
                        require(t, 10);
                        PartSpec part = new PartSpec(t[1], normalize(t[2]), parseFloat(t[3]), parseFloat(t[4]), parseFloat(t[5]),
                            positiveFloat(t[6]), positiveFloat(t[7]), positiveFloat(t[8]),
                            (float) Math.toRadians(parseFloat(t[9])));
                        if (partsById.putIfAbsent(part.id, part) != null)
                            throw new IllegalArgumentException("Duplicate part id '" + part.id + "'.");
                        partSpecs.add(part);
                    }
                    case "FACE" -> {
                        if (version < 2) throw new IllegalArgumentException("Per-face texture mappings require vmodel version 2.");
                        if (t.length < 4 || t.length > 7)
                            throw new IllegalArgumentException("Expected FACE <part> <TOP|BOTTOM|FRONT|BACK|LEFT|RIGHT> <texture> [0|90|180|270] [mirrorU:0|1] [mirrorV:0|1].");
                        PartSpec part = partsById.get(t[1]);
                        if (part == null) throw new IllegalArgumentException("FACE references unknown part '" + t[1] + "'. Define PART before FACE.");
                        String face = t[2].toUpperCase(Locale.ROOT);
                        if (!java.util.Set.of("TOP", "BOTTOM", "FRONT", "BACK", "LEFT", "RIGHT").contains(face))
                            throw new IllegalArgumentException("Unknown face '" + t[2] + "'.");
                        String texture = normalize(t[3]);
                        int rotation = t.length >= 5 ? Integer.parseInt(t[4]) : 0;
                        if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
                            throw new IllegalArgumentException("Face texture rotation must be 0, 90, 180 or 270.");
                        boolean mirrorU = t.length >= 6 && parseBoolean01(t[5], "mirrorU");
                        boolean mirrorV = t.length >= 7 && parseBoolean01(t[6], "mirrorV");
                        if (part.faces.putIfAbsent(face, new TexturedCuboidMeshFactory.FaceTexture(texture, rotation, mirrorU, mirrorV)) != null)
                            throw new IllegalArgumentException("Duplicate mapping for part '" + part.id + "' face " + face + ".");
                    }
                    default -> throw new IllegalArgumentException("Unknown directive '" + t[0] + "'");
                }
            } catch (RuntimeException ex) {
                throw new IOException(path + ":" + lineNumber + ": " + ex.getMessage(), ex);
            }
        }
        if (activeTexture != null) throw new IOException(path + ": missing END_TEXTURE for '" + activeTexture.id + "'.");
        if (!header || id == null || partSpecs.isEmpty())
            throw new IOException("Invalid model: " + path + " (header, NAME and PART required)");
        if (version == 1) {
            var parts = new ArrayList<EntityModel.Part>(partSpecs.size());
            for (PartSpec p : partSpecs) {
                try {
                    parts.add(new EntityModel.Part(p.id, cubeMesh, parseMaterial(p.material), p.x, p.y, p.z,
                        p.width, p.height, p.depth, p.rotationY));
                } catch (RuntimeException ex) {
                    throw new IOException(path + ": unknown material for part '" + p.id + "': " + ex.getMessage(), ex);
                }
            }
            return new EntityModel(id, modelHeight, parts);
        }
        return buildTexturedModel(path, id, modelHeight, partSpecs, pixelTextures);
    }

    private EntityModel buildTexturedModel(Path path, String id, float modelHeight, ArrayList<PartSpec> partSpecs,
                                            LinkedHashMap<String, VModelTextureAtlas.PixelTexture> sourceTextures) throws IOException {
        if (sourceTextures.isEmpty()) throw new IOException("Textured model " + path + " has no TEXTURE blocks.");
        LinkedHashMap<String, VModelTextureAtlas.PixelTexture> allTextures = new LinkedHashMap<>(sourceTextures);
        Map<String, String> defaultTextureByPart = new LinkedHashMap<>();
        try {
            for (PartSpec part : partSpecs) {
                String defaultTexture = sourceTextures.containsKey(part.material) ? part.material : "__solid_" + part.material;
                if (!allTextures.containsKey(defaultTexture)) {
                    Material flat = parseMaterial(part.material);
                    int argb = 0xFF000000 | (Math.round(flat.red() * 255) << 16)
                        | (Math.round(flat.green() * 255) << 8) | Math.round(flat.blue() * 255);
                    allTextures.put(defaultTexture, new VModelTextureAtlas.PixelTexture(defaultTexture, 1, 1, new int[]{argb}));
                }
                defaultTextureByPart.put(part.id, defaultTexture);
                for (var mapping : part.faces.entrySet()) {
                    if (!sourceTextures.containsKey(mapping.getValue().textureId()))
                        throw new IllegalArgumentException("Part '" + part.id + "' face " + mapping.getKey() + " references missing texture '" + mapping.getValue().textureId() + "'.");
                }
            }
            VModelTextureAtlas.Built atlas = VModelTextureAtlas.build(allTextures);
            Material textured = Material.textured("vmodel/" + id, atlas.texture());
            var parts = new ArrayList<EntityModel.Part>(partSpecs.size());
            for (PartSpec p : partSpecs) {
                var meshData = TexturedCuboidMeshFactory.create(defaultTextureByPart.get(p.id), p.faces, atlas.regions());
                Mesh mesh = new Mesh("vmodel/" + id + "/" + p.id, meshData);
                parts.add(new EntityModel.Part(p.id, mesh, textured, p.x, p.y, p.z,
                    p.width, p.height, p.depth, p.rotationY));
            }
            return new EntityModel(id, modelHeight, parts);
        } catch (RuntimeException ex) {
            throw new IOException(path + ": invalid textured model: " + ex.getMessage(), ex);
        }
    }

    private static final class PartSpec {
        final String id, material;
        final float x, y, z, width, height, depth, rotationY;
        final Map<String, TexturedCuboidMeshFactory.FaceTexture> faces = new LinkedHashMap<>();
        PartSpec(String id, String material, float x, float y, float z, float width, float height, float depth, float rotationY) {
            this.id = id; this.material = material; this.x = x; this.y = y; this.z = z;
            this.width = width; this.height = height; this.depth = depth; this.rotationY = rotationY;
        }
    }

    private static final class PixelTextureBuilder {
        final String id;
        final int width, height;
        final Map<Character, Integer> palette = new LinkedHashMap<>();
        final String[] rows;
        PixelTextureBuilder(String id, int width, int height) {
            if (width < 1 || height < 1 || width > 128 || height > 128 || (width & (width - 1)) != 0 || (height & (height - 1)) != 0)
                throw new IllegalArgumentException("Texture dimensions must be power-of-two from 1 to 128.");
            this.id = id; this.width = width; this.height = height; this.rows = new String[height];
        }
        VModelTextureAtlas.PixelTexture build() {
            if (palette.isEmpty()) throw new IllegalArgumentException("Texture '" + id + "' has no PALETTE entries.");
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                if (rows[y] == null) throw new IllegalArgumentException("Texture '" + id + "' is missing pixel row " + y + ".");
                for (int x = 0; x < width; x++) {
                    char symbol = rows[y].charAt(x);
                    Integer color = palette.get(symbol);
                    if (color == null) throw new IllegalArgumentException("Texture '" + id + "' uses undefined palette symbol '" + symbol + "' at " + x + "," + y + ".");
                    pixels[y * width + x] = color;
                }
            }
            return new VModelTextureAtlas.PixelTexture(id, width, height, pixels);
        }
    }

    private static Material parseMaterial(String token) {
        String key = token.toLowerCase(Locale.ROOT);
        Material named = MATERIALS.get(key);
        if (named != null) return named;
        String hex = key.startsWith("#") ? key.substring(1) : key.startsWith("0x") ? key.substring(2) : null;
        if (hex != null && hex.matches("[0-9a-f]{6}")) {
            int rgb = Integer.parseInt(hex, 16);
            return new Material("item_hex_" + hex,
                ((rgb >> 16) & 255) / 255.0f,
                ((rgb >> 8) & 255) / 255.0f,
                (rgb & 255) / 255.0f);
        }
        throw new IllegalArgumentException("Unknown material '" + token + "'. Use a named material or #RRGGBB.");
    }

    private static int parsePixelColor(String token) {
        String hex = token.startsWith("#") ? token.substring(1) : token.startsWith("0x") || token.startsWith("0X") ? token.substring(2) : token;
        if (hex.matches("(?i)[0-9a-f]{6}")) return 0xFF000000 | Integer.parseInt(hex, 16);
        if (hex.matches("(?i)[0-9a-f]{8}")) {
            long rgba = Long.parseLong(hex, 16);
            return (int) (((rgba & 255L) << 24) | ((rgba >>> 8) & 0x00FFFFFFL));
        }
        throw new IllegalArgumentException("Pixel color must be #RRGGBB or #RRGGBBAA: " + token);
    }
    private static boolean parseBoolean01(String value, String field) {
        if (value.equals("0")) return false;
        if (value.equals("1")) return true;
        throw new IllegalArgumentException(field + " must be 0 or 1.");
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT); }
    private static void require(String[] tokens, int count) {
        if (tokens.length != count) throw new IllegalArgumentException("Expected " + (count - 1) + " fields after " + tokens[0]);
    }
    private static float parseFloat(String value) {
        float number = Float.parseFloat(value);
        if (!Float.isFinite(number)) throw new IllegalArgumentException("Number must be finite: " + value);
        return number;
    }
    private static float positiveFloat(String value) {
        float number = parseFloat(value);
        if (number <= 0) throw new IllegalArgumentException("Value must be > 0: " + value);
        return number;
    }
}
