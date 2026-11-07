package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Imports Blockbench's Minecraft Java Block/Item JSON models and PNG texture assets. */
public final class JavaBlockModelLoader {
    private static final Set<String> FACE_NAMES = Set.of("north", "south", "east", "west", "up", "down");
    private static final float COORDINATE_SCALE = 1.0f / 16.0f;
    // Java Block/Item models may intentionally use zero-thickness elements for lantern panes, straps, and plates.
    // Render them as a microscopic slab while marking them ineligible for gameplay collision.
    private static final float MIN_RENDER_THICKNESS = 1.0f / 1024.0f;

    public EntityModel load(Path modelPath, Path textureRoot, Path fallbackTextureRoot) throws IOException {
        modelPath = modelPath.toAbsolutePath().normalize();
        textureRoot = textureRoot.toAbsolutePath().normalize();
        fallbackTextureRoot = fallbackTextureRoot == null ? null : fallbackTextureRoot.toAbsolutePath().normalize();
        if (!Files.isRegularFile(modelPath)) throw new IOException("Blockbench Java model does not exist: " + modelPath);

        Map<String, Object> root = object(MiniJson.read(modelPath), "model root", modelPath);
        List<Object> elements = array(root.get("elements"), "elements", modelPath);
        Map<String, Object> textureRefs = root.containsKey("textures")
            ? object(root.get("textures"), "textures", modelPath) : Map.of();
        // Standard Minecraft Java model UVs live in a 0..16 coordinate space regardless
        // of the PNG's resolution. Older Iskut-generated JSON assets used texture-pixel
        // UVs (for example an 8x8 PNG with UVs 0..8); preserve those without weakening
        // the behavior for actual Blockbench exports.
        String credit = root.get("credit") instanceof String value ? value : "";
        String uvUnits = root.get("iskut_uv_units") instanceof String value ? value.strip() : "";
        boolean legacyTexturePixelUv = uvUnits.equalsIgnoreCase("texture_pixels")
            || credit.startsWith("Converted to Blockbench Minecraft Java Block/Item JSON from the original Iskut model");
        int textureWidth = 16, textureHeight = 16;
        if (root.containsKey("texture_size")) {
            List<Object> size = array(root.get("texture_size"), "texture_size", modelPath);
            if (size.size() != 2) throw bad(modelPath, "texture_size must contain [width, height]");
            textureWidth = positiveInt(size.get(0), "texture_size[0]", modelPath);
            textureHeight = positiveInt(size.get(1), "texture_size[1]", modelPath);
        }

        LinkedHashMap<String, VModelTextureAtlas.PixelTexture> pixelTextures = new LinkedHashMap<>();
        Map<String, String> resolvedTexturePaths = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : textureRefs.entrySet()) {
            if (!(entry.getValue() instanceof String)) continue; // allow non-texture JSON metadata in future exports
            String textureValue = resolveTextureReference(entry.getKey(), textureRefs, new LinkedHashSet<>(), modelPath);
            Path textureFile = findTexture(textureValue, textureRoot, fallbackTextureRoot);
            BufferedImage image = ImageIO.read(textureFile.toFile());
            if (image == null) throw bad(modelPath, "unsupported texture image: " + textureFile);
            int width = image.getWidth(), height = image.getHeight();
            if (width < 1 || height < 1 || width > 512 || height > 512)
                throw bad(modelPath, "texture '" + textureValue + "' must be between 1x1 and 512x512 (got " + width + "x" + height + ")");
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            String key = entry.getKey();
            pixelTextures.put(key, new VModelTextureAtlas.PixelTexture(key, width, height, pixels));
            resolvedTexturePaths.put(key, textureValue.replace('\\', '/'));
        }
        if (pixelTextures.isEmpty()) throw bad(modelPath, "textures must contain at least one loadable PNG reference");

        VModelTextureAtlas.Built atlas;
        try { atlas = VModelTextureAtlas.build(pixelTextures); }
        catch (RuntimeException ex) { throw bad(modelPath, "could not build model texture atlas: " + ex.getMessage(), ex); }
        Material material = Material.textured("blockbench-java/" + stripExtension(modelPath.getFileName().toString()), atlas.texture());

        ArrayList<EntityModel.Part> parts = new ArrayList<>();
        Set<String> partIds = new LinkedHashSet<>();
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int index = 0;
        for (Object rawElement : elements) {
            Map<String, Object> element = object(rawElement, "elements[" + index + "]", modelPath);
            String name = element.get("name") instanceof String s && !s.isBlank() ? s : "element_" + index;
            String partId = uniqueId(name, partIds);
            List<Object> from = vector(element.get("from"), "elements[" + index + "].from", modelPath);
            List<Object> to = vector(element.get("to"), "elements[" + index + "].to", modelPath);
            float x0 = number(from.get(0), "from.x", modelPath), y0 = number(from.get(1), "from.y", modelPath), z0 = number(from.get(2), "from.z", modelPath);
            float x1 = number(to.get(0), "to.x", modelPath), y1 = number(to.get(1), "to.y", modelPath), z1 = number(to.get(2), "to.z", modelPath);
            float rawWidth = Math.abs(x1 - x0) * COORDINATE_SCALE;
            float rawHeight = Math.abs(y1 - y0) * COORDINATE_SCALE;
            float rawDepth = Math.abs(z1 - z0) * COORDINATE_SCALE;
            boolean collisionEligible = rawWidth > 1.0e-5f && rawHeight > 1.0e-5f && rawDepth > 1.0e-5f;
            float width = Math.max(rawWidth, MIN_RENDER_THICKNESS);
            float height = Math.max(rawHeight, MIN_RENDER_THICKNESS);
            float depth = Math.max(rawDepth, MIN_RENDER_THICKNESS);

            float centerX = ((x0 + x1) * 0.5f - 8.0f) * COORDINATE_SCALE;
            float centerY = ((y0 + y1) * 0.5f - 8.0f) * COORDINATE_SCALE;
            float centerZ = ((z0 + z1) * 0.5f - 8.0f) * COORDINATE_SCALE;
            float rotationX = 0, rotationY = 0, rotationZ = 0;
            float originX = 0, originY = 0, originZ = 0;
            if (element.containsKey("rotation")) {
                Map<String, Object> rotation = object(element.get("rotation"), "element.rotation", modelPath);
                float angle = number(rotation.getOrDefault("angle", 0.0), "rotation.angle", modelPath);
                if (Math.abs(angle) > 180.0f) throw bad(modelPath, "element '" + partId + "' rotation angle must be within -180..180 degrees");
                String axis = rotation.get("axis") instanceof String s ? s.toLowerCase(Locale.ROOT) : "y";
                List<Object> origin = rotation.containsKey("origin")
                    ? vector(rotation.get("origin"), "rotation.origin", modelPath) : List.of(8.0, 8.0, 8.0);
                originX = (number(origin.get(0), "origin.x", modelPath) - 8.0f) * COORDINATE_SCALE;
                originY = (number(origin.get(1), "origin.y", modelPath) - 8.0f) * COORDINATE_SCALE;
                originZ = (number(origin.get(2), "origin.z", modelPath) - 8.0f) * COORDINATE_SCALE;
                float radians = (float) Math.toRadians(angle);
                switch (axis) {
                    case "x" -> rotationX = radians;
                    case "y" -> rotationY = radians;
                    case "z" -> rotationZ = radians;
                    default -> throw bad(modelPath, "element '" + partId + "' has unsupported rotation axis '" + axis + "'");
                }
            }
            float[] rotatedCenter = rotateAroundPivot(centerX, centerY, centerZ, originX, originY, originZ,
                rotationX, rotationY, rotationZ);

            Map<String, TexturedCuboidMeshFactory.FaceTexture> faceMap = new LinkedHashMap<>();
            Map<String, Object> faces = element.containsKey("faces")
                ? object(element.get("faces"), "element.faces", modelPath) : Map.of();
            for (String face : FACE_NAMES) {
                Object faceValue = faces.get(face);
                String factoryFace = switch (face) {
                    case "north" -> "BACK";
                    case "south" -> "FRONT";
                    case "east" -> "RIGHT";
                    case "west" -> "LEFT";
                    case "up" -> "TOP";
                    case "down" -> "BOTTOM";
                    default -> throw new IllegalStateException("unreachable face");
                };
                if (faceValue == null) {
                    faceMap.put(factoryFace, TexturedCuboidMeshFactory.FaceTexture.skipped());
                    continue;
                }
                Map<String, Object> faceData = object(faceValue, "element.faces." + face, modelPath);
                Object textureValue = faceData.get("texture");
                if (!(textureValue instanceof String textureName) || textureName.isBlank()) {
                    faceMap.put(factoryFace, TexturedCuboidMeshFactory.FaceTexture.skipped());
                    continue;
                }
                String textureId = textureName.startsWith("#") ? textureName.substring(1) : directTextureKey(textureName, resolvedTexturePaths);
                if (!atlas.regions().containsKey(textureId)) throw bad(modelPath, "face '" + face + "' references undefined texture '" + textureName + "'");
                int rotation = faceData.containsKey("rotation") ? Math.round(number(faceData.get("rotation"), "face.rotation", modelPath)) : 0;
                if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270)
                    throw bad(modelPath, "face '" + face + "' rotation must be 0, 90, 180, or 270 degrees");
                float[] uvCoordinates;
                boolean hasExplicitUv = faceData.containsKey("uv");
                if (hasExplicitUv) {
                    List<Object> uv = array(faceData.get("uv"), "face.uv", modelPath);
                    if (uv.size() != 4) throw bad(modelPath, "face '" + face + "' uv must contain [u1, v1, u2, v2]");
                    uvCoordinates = new float[]{
                        number(uv.get(0), "uv.u1", modelPath), number(uv.get(1), "uv.v1", modelPath),
                        number(uv.get(2), "uv.u2", modelPath), number(uv.get(3), "uv.v2", modelPath)
                    };
                } else {
                    // Java default UV projection is expressed in the same 0..16 UV space.
                    // The geometry is converted from model units to world units separately.
                    uvCoordinates = defaultFaceUv(face, x0, y0, z0, x1, y1, z1, textureWidth, textureHeight);
                }
                float divisorU = hasExplicitUv && legacyTexturePixelUv
                    ? pixelTextures.get(textureId).width() : (hasExplicitUv ? 16.0f : textureWidth);
                float divisorV = hasExplicitUv && legacyTexturePixelUv
                    ? pixelTextures.get(textureId).height() : (hasExplicitUv ? 16.0f : textureHeight);
                float u0 = uvCoordinates[0] / divisorU;
                float v0 = uvCoordinates[1] / divisorV;
                float u1 = uvCoordinates[2] / divisorU;
                float v1 = uvCoordinates[3] / divisorV;
                TexturedCuboidMeshFactory.FaceTexture mapping = new TexturedCuboidMeshFactory.FaceTexture(
                    textureId, rotation, false, false, u0, v0, u1, v1, true);
                faceMap.put(factoryFace, mapping);
            }
            if (faces.isEmpty()) throw bad(modelPath, "element '" + partId + "' has no face texture mappings");
            var meshData = TexturedCuboidMeshFactory.create(pixelTextures.keySet().iterator().next(), faceMap, atlas.regions());
            if (meshData.vertexCount() == 0) { index++; continue; }
            Mesh mesh;
            try { mesh = new Mesh("blockbench-java/" + stripExtension(modelPath.getFileName().toString()) + "/" + partId, meshData); }
            catch (RuntimeException ex) { throw bad(modelPath, "invalid geometry in element '" + partId + "': " + ex.getMessage(), ex); }
            parts.add(new EntityModel.Part(partId, mesh, material, rotatedCenter[0], rotatedCenter[1], rotatedCenter[2],
                width, height, depth, rotationX, rotationY, rotationZ, collisionEligible));
            minY = Math.min(minY, rotatedCenter[1] - height * 0.5f);
            maxY = Math.max(maxY, rotatedCenter[1] + height * 0.5f);
            index++;
        }
        if (parts.isEmpty()) throw bad(modelPath, "model contains no renderable cuboid elements");
        float modelHeight = Math.max(0.01f, maxY - Math.min(0.0f, minY));
        return new EntityModel(stripExtension(modelPath.getFileName().toString()), modelHeight, parts);
    }

    /**
     * Java Block/Item's standard omitted-UV projection in UV units (16 units span
     * the complete texture regardless of the PNG resolution). The declared texture
     * size converts this UV space to source-pixel coordinates for the legacy atlas
     * builder, after which it is normalized back to the source texture region.
     */
    private static float[] defaultFaceUv(String face, float fromX, float fromY, float fromZ,
                                          float toX, float toY, float toZ,
                                          int textureWidth, int textureHeight) {
        float sx = textureWidth / 16.0f;
        float sy = textureHeight / 16.0f;
        float u0, v0, u1, v1;
        switch (face) {
            case "down" -> {
                u0 = fromX * sx; v0 = textureHeight - toZ * sy;
                u1 = toX * sx;   v1 = textureHeight - fromZ * sy;
            }
            case "up" -> {
                u0 = fromX * sx; v0 = fromZ * sy;
                u1 = toX * sx;   v1 = toZ * sy;
            }
            case "north" -> {
                u0 = textureWidth - toX * sx; v0 = textureHeight - toY * sy;
                u1 = textureWidth - fromX * sx; v1 = textureHeight - fromY * sy;
            }
            case "south" -> {
                u0 = fromX * sx; v0 = textureHeight - toY * sy;
                u1 = toX * sx;   v1 = textureHeight - fromY * sy;
            }
            case "west" -> {
                u0 = fromZ * sx; v0 = textureHeight - toY * sy;
                u1 = toZ * sx;   v1 = textureHeight - fromY * sy;
            }
            case "east" -> {
                u0 = textureWidth - toZ * sx; v0 = textureHeight - toY * sy;
                u1 = textureWidth - fromZ * sx; v1 = textureHeight - fromY * sy;
            }
            default -> throw new IllegalArgumentException("Unsupported Java model face: " + face);
        }
        return new float[]{u0, v0, u1, v1};
    }

    private static Path findTexture(String raw, Path textureRoot, Path fallbackRoot) throws IOException {
        String normalized = raw.replace('\\', '/').strip();
        int namespace = normalized.indexOf(':');
        if (namespace >= 0) normalized = normalized.substring(namespace + 1);
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.toLowerCase(Locale.ROOT).endsWith(".png")) normalized = normalized.substring(0, normalized.length() - 4);
        Path relative = Path.of(normalized).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) throw new IOException("Unsafe Blockbench texture path: " + raw);
        Path candidate = textureRoot.resolve(relative + ".png").normalize();
        if (candidate.startsWith(textureRoot) && Files.isRegularFile(candidate)) return candidate;
        if (fallbackRoot != null) {
            Path fallback = fallbackRoot.resolve(relative.getFileName().toString() + ".png").normalize();
            if (Files.isRegularFile(fallback)) return fallback;
            fallback = fallbackRoot.resolve(relative).normalize();
            if (fallback.startsWith(fallbackRoot) && Files.isRegularFile(fallback)) return fallback;
        }
        throw new IOException("Missing Blockbench texture '" + raw + "'. Expected " + candidate
            + (fallbackRoot == null ? "" : " (fallback root: " + fallbackRoot + ")"));
    }

    private static String resolveTextureReference(String key, Map<String, Object> textureRefs,
                                                  Set<String> chain, Path modelPath) throws IOException {
        if (!chain.add(key)) throw bad(modelPath, "cyclic texture reference: " + String.join(" -> ", chain) + " -> " + key);
        Object raw = textureRefs.get(key);
        if (!(raw instanceof String value) || value.isBlank())
            throw bad(modelPath, "texture reference '#" + key + "' is undefined or is not a string");
        String normalized = value.strip();
        if (normalized.startsWith("#"))
            return resolveTextureReference(normalized.substring(1), textureRefs, chain, modelPath);
        return normalized;
    }

    private static String directTextureKey(String textureName, Map<String, String> resolvedTexturePaths) {
        String normalized = textureName.replace('\\', '/');
        for (Map.Entry<String, String> entry : resolvedTexturePaths.entrySet())
            if (entry.getValue().equals(normalized)) return entry.getKey();
        return textureName;
    }

    private static float[] rotateAroundPivot(float x, float y, float z, float px, float py, float pz,
                                             float rx, float ry, float rz) {
        float dx = x - px, dy = y - py, dz = z - pz;
        // Same Rz * Ry * Rx convention as the engine Transform class.
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
        float x1 = dx;
        float y1 = cx * dy - sx * dz;
        float z1 = sx * dy + cx * dz;
        float x2 = cy * x1 + sy * z1;
        float y2 = y1;
        float z2 = -sy * x1 + cy * z1;
        return new float[]{px + cz * x2 - sz * y2, py + sz * x2 + cz * y2, pz + z2};
    }

    private static String uniqueId(String candidate, Set<String> used) {
        String base = candidate.replaceAll("[^A-Za-z0-9_-]", "_");
        if (base.isBlank()) base = "element";
        String result = base;
        int suffix = 2;
        while (!used.add(result)) result = base + "_" + suffix++;
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field, Path path) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw bad(path, field + " must be a JSON object");
        for (Object key : map.keySet()) if (!(key instanceof String)) throw bad(path, field + " has a non-string key");
        return (Map<String, Object>) map;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, String field, Path path) throws IOException {
        if (!(value instanceof List<?> list)) throw bad(path, field + " must be a JSON array");
        return (List<Object>) list;
    }
    private static List<Object> vector(Object value, String field, Path path) throws IOException {
        List<Object> result = array(value, field, path);
        if (result.size() != 3) throw bad(path, field + " must contain exactly three coordinates");
        return result;
    }
    private static float number(Object value, String field, Path path) throws IOException {
        if (!(value instanceof Number number)) throw bad(path, field + " must be a number");
        double d = number.doubleValue();
        if (!Double.isFinite(d) || d < -1.0e6 || d > 1.0e6) throw bad(path, field + " is outside the supported finite range");
        return (float) d;
    }
    private static int positiveInt(Object value, String field, Path path) throws IOException {
        float number = number(value, field, path);
        if (number < 1 || number > 2048 || number != Math.rint(number)) throw bad(path, field + " must be an integer from 1 to 2048");
        return (int) number;
    }
    private static String stripExtension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }
    private static IOException bad(Path path, String message) { return new IOException(path + ": " + message); }
    private static IOException bad(Path path, String message, Throwable cause) { return new IOException(path + ": " + message, cause); }
}
