package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.client.entity.JavaBlockModelLoader;
import dev.itzsiden.skyvoxel.client.entity.ModelTransformUtil;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Loads hand-authored, renderer-independent .vmap/.vstruct source files. No terrain generation is
 * performed. Files are deliberately line-oriented so the future custom map editor can export them
 * without pulling a JSON library into the runtime.
 *
 * .vmap:
 *   SKYVOXEL_MAP 1
 *   MAP main_hub
 *   SPAWN x y z yawDegrees
 *   PLACE instanceId ../structures/plaza.vstruct x y z yawDegrees
 *
 * .vstruct:
 *   SKYVOXEL_STRUCTURE 1
 *   NAME plaza
 *   CUBOID id material x y z width height depth solid [localYawDegrees]
 *   PLANE id material x y z width depth                 # top-only, non-solid plane
 *   MODEL id ../blocks/models/model.json x y z scale solid [localYawDegrees]
 *   INTERACT id TYPE title_token message_token
 */
public final class MapLoader {
    private static final int MAP_VERSION = 1;
    private static final int STRUCTURE_VERSION = 1;
    private static final Map<String, Mesh> TEXTURED_MESHES = new java.util.concurrent.ConcurrentHashMap<>();

    private MapLoader() {}

    public static GameMap load(Path mapFile) throws IOException {
        Objects.requireNonNull(mapFile, "mapFile");
        Path absoluteMap = mapFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluteMap)) {
            throw new IOException("Map file does not exist: " + absoluteMap
                + "\nExpected a handcrafted .vmap file. Use --map=path/to/map.vmap or add data/maps/main_hub.vmap.");
        }

        List<String> lines = Files.readAllLines(absoluteMap);
        String mapName = stripExtension(absoluteMap.getFileName().toString());
        GameMap.Spawn spawn = new GameMap.Spawn(0, 0, 12, 0);
        boolean headerSeen = false;
        Scene scene = new Scene();
        Map<String, GameMap.Interaction> interactions = new LinkedHashMap<>();
        Path dataRoot = absoluteMap.getParent().getParent().normalize();
        Path textureRoot = dataRoot.resolve("textures").resolve("world");
        Map<String, Material> materials = WorldTextureLibrary.load(textureRoot);
        Map<Path, EntityModel> javaModelCache = new HashMap<>();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String[] t = tokens(lines.get(lineIndex));
            if (t.length == 0) continue;
            String where = absoluteMap.getFileName() + ":" + (lineIndex + 1);
            switch (t[0].toUpperCase(Locale.ROOT)) {
                case "SKYVOXEL_MAP" -> {
                    require(t, 2, where);
                    int version = integer(t[1], where);
                    if (version != MAP_VERSION) throw format(where, "Unsupported map version " + version + ". Expected " + MAP_VERSION + ".");
                    headerSeen = true;
                }
                case "MAP" -> {
                    require(t, 2, where);
                    mapName = t[1].replace('_', ' ');
                }
                case "SPAWN" -> {
                    require(t, 5, where);
                    spawn = new GameMap.Spawn(number(t[1], where), number(t[2], where), number(t[3], where),
                        (float) Math.toRadians(number(t[4], where)));
                }
                case "PLACE" -> {
                    require(t, 7, where);
                    String instanceId = safeId(t[1], where);
                    Path structurePath = absoluteMap.getParent().resolve(t[2]).normalize();
                    float px = number(t[3], where), py = number(t[4], where), pz = number(t[5], where);
                    float yaw = (float) Math.toRadians(number(t[6], where));
                    if (!structurePath.startsWith(dataRoot)) throw format(where, "Structure path escapes the data/ directory.");
                    StructureData structure = loadStructure(structurePath, materials, dataRoot, javaModelCache);
                    placeStructure(scene, interactions, instanceId, structure, px, py, pz, yaw, where, dataRoot, javaModelCache);
                }
                default -> throw format(where, "Unknown map directive '" + t[0] + "'.");
            }
        }
        if (!headerSeen) throw format(absoluteMap.getFileName().toString(), "Missing SKYVOXEL_MAP 1 header.");
        if (scene.size() == 0) throw format(absoluteMap.getFileName().toString(), "Map contains no objects.");
        return new GameMap(mapName, scene, StaticWorldBatcher.build(scene, interactions.keySet()), spawn, interactions);
    }

    private static StructureData loadStructure(Path path, Map<String, Material> materials, Path dataRoot,
                                               Map<Path, EntityModel> javaModelCache) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Structure file does not exist: " + path);
        List<String> lines = Files.readAllLines(path);
        String name = stripExtension(path.getFileName().toString());
        boolean headerSeen = false;
        Map<String, CuboidDef> cuboids = new LinkedHashMap<>();
        Map<String, PlaneDef> planes = new LinkedHashMap<>();
        Map<String, ModelPlacementDef> models = new LinkedHashMap<>();
        Map<String, GameMap.Interaction> interactions = new LinkedHashMap<>();

        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String[] t = tokens(lines.get(lineIndex));
            if (t.length == 0) continue;
            String where = path.getFileName() + ":" + (lineIndex + 1);
            switch (t[0].toUpperCase(Locale.ROOT)) {
                case "SKYVOXEL_STRUCTURE" -> {
                    require(t, 2, where);
                    int version = integer(t[1], where);
                    if (version != STRUCTURE_VERSION) throw format(where, "Unsupported structure version " + version + ". Expected " + STRUCTURE_VERSION + ".");
                    headerSeen = true;
                }
                case "NAME" -> {
                    require(t, 2, where);
                    name = t[1].replace('_', ' ');
                }
                case "CUBOID" -> {
                    if (t.length != 10 && t.length != 11) throw format(where,
                        "CUBOID syntax: CUBOID id material x y z width height depth solid [localYawDegrees]");
                    String id = safeId(t[1], where);
                    Material material = materials.get(t[2].toLowerCase(Locale.ROOT));
                    if (material == null) throw format(where, "Unknown material '" + t[2] + "'. Available: " + String.join(", ", materials.keySet()));
                    float x = number(t[3], where), y = number(t[4], where), z = number(t[5], where);
                    float width = positive(number(t[6], where), where, "width");
                    float height = positive(number(t[7], where), where, "height");
                    float depth = positive(number(t[8], where), where, "depth");
                    boolean solid = bool(t[9], where);
                    float yaw = t.length == 11 ? (float) Math.toRadians(number(t[10], where)) : 0.0f;
                    if (cuboids.containsKey(id) || planes.containsKey(id) || models.containsKey(id))
                        throw format(where, "Duplicate structure element ID '" + id + "'.");
                    cuboids.put(id, new CuboidDef(id, material, x, y, z, width, height, depth, solid, yaw));
                }
                case "PLANE" -> {
                    require(t, 8, where);
                    String id = safeId(t[1], where);
                    Material material = materials.get(t[2].toLowerCase(Locale.ROOT));
                    if (material == null) throw format(where, "Unknown material '" + t[2] + "'. Available: " + String.join(", ", materials.keySet()));
                    PlaneDef plane = new PlaneDef(id, material, number(t[3], where), number(t[4], where), number(t[5], where),
                        positive(number(t[6], where), where, "width"), positive(number(t[7], where), where, "depth"));
                    if (planes.putIfAbsent(id, plane) != null || cuboids.containsKey(id) || models.containsKey(id))
                        throw format(where, "Duplicate structure element ID '" + id + "'.");
                }
                case "MODEL" -> {
                    if (t.length != 8 && t.length != 9) throw format(where,
                        "MODEL syntax: MODEL id relativeModelJson x y z scale solid [localYawDegrees]");
                    String id = safeId(t[1], where);
                    Path modelPath = path.getParent().resolve(t[2]).normalize().toAbsolutePath();
                    if (!modelPath.startsWith(dataRoot)) throw format(where, "Blockbench model path escapes the data/ directory.");
                    float x = number(t[3], where), y = number(t[4], where), z = number(t[5], where);
                    float scale = positive(number(t[6], where), where, "scale");
                    boolean solid = bool(t[7], where);
                    float yaw = t.length == 9 ? (float) Math.toRadians(number(t[8], where)) : 0.0f;
                    ModelPlacementDef model = new ModelPlacementDef(id, modelPath, x, y, z, scale, solid, yaw);
                    if (models.putIfAbsent(id, model) != null || cuboids.containsKey(id) || planes.containsKey(id))
                        throw format(where, "Duplicate structure element ID '" + id + "'.");
                }
                case "INTERACT" -> {
                    require(t, 5, where);
                    String id = safeId(t[1], where);
                    GameMap.InteractionType type;
                    try { type = GameMap.InteractionType.valueOf(t[2].toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException ex) { throw format(where, "Unknown interaction type '" + t[2] + "'."); }
                    if (interactions.putIfAbsent(id, new GameMap.Interaction(type, t[3], t[4])) != null) {
                        throw format(where, "Duplicate interaction for object '" + id + "'.");
                    }
                }
                default -> throw format(where, "Unknown structure directive '" + t[0] + "'.");
            }
        }
        if (!headerSeen) throw format(path.getFileName().toString(), "Missing SKYVOXEL_STRUCTURE 1 header.");
        for (String objectId : interactions.keySet()) {
            if (!cuboids.containsKey(objectId) && !models.containsKey(objectId))
                throw format(path.getFileName().toString(), "Interaction points to missing CUBOID/MODEL element '" + objectId + "'.");
        }
        if (cuboids.isEmpty() && planes.isEmpty() && models.isEmpty())
            throw format(path.getFileName().toString(), "Structure contains no CUBOID, PLANE or MODEL entries.");
        return new StructureData(name, new ArrayList<>(cuboids.values()), new ArrayList<>(planes.values()),
            new ArrayList<>(models.values()), interactions);
    }

    private static void placeStructure(Scene scene, Map<String, GameMap.Interaction> allInteractions,
                                       String instanceId, StructureData structure,
                                       float px, float py, float pz, float placementYaw, String where,
                                       Path dataRoot, Map<Path, EntityModel> javaModelCache) throws IOException {
        float cos = (float) Math.cos(placementYaw), sin = (float) Math.sin(placementYaw);
        for (CuboidDef def : structure.cuboids()) {
            String placedId = instanceId + "/" + def.id();
            float worldX = px + cos * def.x() + sin * def.z();
            float worldZ = pz - sin * def.x() + cos * def.z();
            Transform transform = new Transform()
                .setPosition(worldX, py + def.y(), worldZ)
                .setRotation(0, placementYaw + def.yawRadians(), 0)
                .setScale(def.width(), def.height(), def.depth());
            float tileWorldSize = textureTileWorldSize(def.material().name());
            String meshKey = Float.toString(def.width()) + "/" + Float.toString(def.height()) + "/"
                + Float.toString(def.depth()) + "/tile=" + Float.toString(tileWorldSize);
            Mesh texturedMesh = TEXTURED_MESHES.computeIfAbsent(meshKey, ignored -> new Mesh("world-cuboid-" + meshKey,
                WorldCuboidMeshFactory.create(def.width(), def.height(), def.depth(), tileWorldSize)));
            RenderObject object = new RenderObject(placedId, texturedMesh, def.material(), transform)
                .setCollidable(def.solid());
            try { scene.add(object); }
            catch (IllegalArgumentException ex) { throw format(where, ex.getMessage()); }
            GameMap.Interaction interaction = structure.interactions().get(def.id());
            if (interaction != null) allInteractions.put(placedId, interaction);
        }
        for (PlaneDef def : structure.planes()) {
            String placedId = instanceId + "/" + def.id();
            float worldX = px + cos * def.x() + sin * def.z();
            float worldZ = pz - sin * def.x() + cos * def.z();
            float tileWorldSize = textureTileWorldSize(def.material().name());
            String meshKey = "plane/" + def.width() + "/" + def.depth() + "/tile=" + tileWorldSize;
            Mesh planeMesh = TEXTURED_MESHES.computeIfAbsent(meshKey, ignored -> new Mesh(meshKey,
                WorldPlaneMeshFactory.create(def.width(), def.depth(), tileWorldSize)));
            Transform transform = new Transform().setPosition(worldX, py + def.y(), worldZ)
                .setRotation(0, placementYaw, 0).setScale(def.width(), 1.0f, def.depth());
            RenderObject plane = new RenderObject(placedId, planeMesh, def.material(), transform)
                .setCollidable(false).setStaticBatchExcluded(true);
            try { scene.add(plane); }
            catch (IllegalArgumentException ex) { throw format(where, ex.getMessage()); }
        }
        JavaBlockModelLoader modelLoader = new JavaBlockModelLoader();
        for (ModelPlacementDef def : structure.models()) {
            EntityModel model;
            Path modelPath = def.modelPath().toAbsolutePath().normalize();
            try {
                model = javaModelCache.get(modelPath);
                if (model == null) {
                    Path textureRoot = modelPath.getParent().getParent().resolve("textures").normalize();
                    model = modelLoader.load(modelPath, textureRoot, dataRoot.resolve("textures/world"));
                    javaModelCache.put(modelPath, model);
                }
            } catch (IOException | RuntimeException ex) {
                throw new IOException(where + ": failed to load Blockbench structure model '" + modelPath + "': " + ex.getMessage(), ex);
            }
            float modelYaw = placementYaw + def.yawRadians();
            float modelCos = (float) Math.cos(modelYaw), modelSin = (float) Math.sin(modelYaw);
            for (EntityModel.Part part : model.parts()) {
                String placedId = instanceId + "/" + def.id() + "/" + part.id();
                float localX = def.x() + part.offsetX() * def.scale();
                float localZ = def.z() + part.offsetZ() * def.scale();
                float worldX = px + modelCos * localX + modelSin * localZ;
                float worldZ = pz - modelSin * localX + modelCos * localZ;
                float worldY = py + def.y() + part.offsetY() * def.scale();
                Transform transform = new Transform();
                ModelTransformUtil.setPlacedPart(transform, worldX, worldY, worldZ, modelYaw, part, def.scale());
                RenderObject object = new RenderObject(placedId, part.mesh(), part.material(), transform)
                    .setCollidable(def.solid() && part.collisionEligible());
                try { scene.add(object); }
                catch (IllegalArgumentException ex) { throw format(where, ex.getMessage()); }
                GameMap.Interaction interaction = structure.interactions().get(def.id());
                if (interaction != null) allInteractions.put(placedId, interaction);
            }
        }
    }


    /** Larger texel footprint for broad ocean/beach materials; hard tiles stay crisp on architecture. */
    private static float textureTileWorldSize(String materialId) {
        return switch (materialId.toLowerCase(Locale.ROOT)) {
            case "water" -> 16.0f;
            case "shallow_water" -> 5.0f;
            case "foam" -> 0.95f;
            case "shore_transition" -> 2.7f;
            case "wet_sand" -> 1.8f;
            case "sand" -> 1.8f;
            case "dirt" -> 1.5f;
            default -> 1.0f;
        };
    }

    private static String[] tokens(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return new String[0];
        int comment = trimmed.indexOf(" #");
        if (comment >= 0) trimmed = trimmed.substring(0, comment).stripTrailing();
        return trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    }

    private static void require(String[] tokens, int expected, String where) {
        if (tokens.length != expected) throw format(where, "Expected " + expected + " fields for " + tokens[0] + ", got " + tokens.length + ".");
    }

    private static float number(String value, String where) {
        try {
            float result = Float.parseFloat(value);
            if (!Float.isFinite(result)) throw new NumberFormatException("non-finite");
            return result;
        } catch (NumberFormatException ex) { throw format(where, "Invalid finite number '" + value + "'."); }
    }

    private static int integer(String value, String where) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw format(where, "Invalid integer '" + value + "'."); }
    }

    private static boolean bool(String value, String where) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw format(where, "Expected true or false, got '" + value + "'.");
    }

    private static float positive(float value, String where, String field) {
        if (value <= 0) throw format(where, field + " must be > 0.");
        return value;
    }

    private static String safeId(String id, String where) {
        if (!id.matches("[A-Za-z0-9_-]+")) throw format(where, "Invalid ID '" + id + "'. Use letters, digits, underscore or hyphen.");
        return id;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static IllegalArgumentException format(String where, String message) {
        return new IllegalArgumentException("Map format error at " + where + ": " + message);
    }

    private record CuboidDef(String id, Material material, float x, float y, float z,
                             float width, float height, float depth, boolean solid, float yawRadians) {}
    private record PlaneDef(String id, Material material, float x, float y, float z, float width, float depth) {}
    private record ModelPlacementDef(String id, Path modelPath, float x, float y, float z,
                                     float scale, boolean solid, float yawRadians) {}
    private record StructureData(String name, List<CuboidDef> cuboids, List<PlaneDef> planes,
                                 List<ModelPlacementDef> models, Map<String, GameMap.Interaction> interactions) {}
}
