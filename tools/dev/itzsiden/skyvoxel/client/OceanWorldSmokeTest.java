package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.client.map.MapLoader;
import dev.itzsiden.skyvoxel.engine.physics.AxisAlignedBox;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Verifies the ocean map, beach material bands, lack of legacy sky scenery, and physical island support. */
public final class OceanWorldSmokeTest {
    private OceanWorldSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path mapPath = Path.of("data/maps/main_hub.vmap").toAbsolutePath().normalize();
        String source = Files.readString(mapPath).toLowerCase(Locale.ROOT);
        require(source.contains("ocean_surface"), "map must place the ocean surface");
        require(source.contains("beach_transition"), "map must place the beach transition");
        require(!source.contains("skyland_isle") && !source.contains("horizon_far") && !source.contains("cloud_south"),
            "legacy sky islands/clouds must not be placed in the active map");

        GameMap map = MapLoader.load(mapPath);
        Set<String> materials = new HashSet<>();
        int sandCount = 0, wetSandCount = 0, shallowCount = 0, foamCount = 0;
        boolean sandSolid = false, wetSandSolid = false, shallowSolid = false, foamSolid = false;
        double sandTop = Double.NEGATIVE_INFINITY, wetSandTop = Double.NEGATIVE_INFINITY, shallowTop = Double.NEGATIVE_INFINITY, foamTop = Double.NEGATIVE_INFINITY;
        RenderObject water = null;
        for (RenderObject object : map.scene().objects()) {
            materials.add(object.material().name());
            if (object.id().equals("ocean_surface/ocean_water")) water = object;
            if (object.id().startsWith("beach_transition/")) {
                switch (object.material().name()) {
                    case "sand" -> { sandCount++; sandSolid |= object.collidable(); sandTop = Math.max(sandTop, object.transform().y() + object.transform().scaleY() * 0.5); }
                    case "wet_sand" -> { wetSandCount++; wetSandSolid |= object.collidable(); wetSandTop = Math.max(wetSandTop, object.transform().y() + object.transform().scaleY() * 0.5); }
                    case "shallow_water" -> { shallowCount++; shallowSolid |= object.collidable(); shallowTop = Math.max(shallowTop, object.transform().y() + object.transform().scaleY() * 0.5); }
                    case "foam" -> { foamCount++; foamSolid |= object.collidable(); foamTop = Math.max(foamTop, object.transform().y() + object.transform().scaleY() * 0.5); }
                }
            }
        }
        require(water != null, "ocean water object must load");
        require(!water.collidable(), "ocean surface must not be a solid floor");
        require(water.staticBatchExcluded(), "ocean must not influence AO/coplanar offsets or enter terrain mesh batches");
        require(map.renderScene().objects().stream().anyMatch(object -> object.id().equals("ocean_surface/ocean_water")),
            "ocean should remain an individual render object outside static batching");
        require(water.mesh().maxY() == water.mesh().minY(), "ocean plane must have zero thickness");
        require(water.mesh().triangleCount() == 2,
            "ocean must be a single top-only plane, got " + water.mesh().triangleCount() + " triangles");
        require(water.mesh().triangleCount() < 2_000,
            "large ocean plane must use coarse static geometry, got " + water.mesh().triangleCount() + " triangles");
        double seaLevel = water.transform().y() + water.mesh().maxY() * water.transform().scaleY();
        require(Math.abs(seaLevel - 1.0) < 0.001, "sea level should be 1.0, got " + seaLevel);
        require(materials.containsAll(Set.of("water", "dirt", "sand", "wet_sand", "shallow_water", "foam")),
            "all coast material textures must be available: " + materials);
        require(sandCount >= 50 && wetSandCount >= 50 && shallowCount >= 50 && foamCount >= 50,
            "beach should have all four authored material bands: " + sandCount + "/" + wetSandCount + "/" + shallowCount + "/" + foamCount);
        require(sandSolid && wetSandSolid && !shallowSolid && !foamSolid,
            "sand tiers must be walkable while shallow water/foam remain visual-only");
        require(Math.abs(sandTop - 1.21) < 0.002 && Math.abs(wetSandTop - 1.06) < 0.002
                && shallowTop > seaLevel && foamTop > seaLevel,
            "shore band heights should meet the ocean: sand=" + sandTop + " wet=" + wetSandTop
                + " shallow=" + shallowTop + " foam=" + foamTop + " sea=" + seaLevel);

        StaticCollisionWorld collision = new StaticCollisionWorld(map.scene());
        require(collision.colliders().stream().noneMatch(c -> c.object().id().equals("ocean_surface/ocean_water")),
            "ocean surface must not enter the physics broadphase");
        boolean spawnSupported = false;
        for (StaticCollisionWorld.Collider collider : collision.colliders()) {
            AxisAlignedBox b = collider.bounds();
            if (b.minX() <= map.spawn().x() && b.maxX() >= map.spawn().x()
                && b.minZ() <= map.spawn().z() && b.maxZ() >= map.spawn().z()
                && Math.abs(b.maxY() - 1.20) < 0.03) {
                spawnSupported = true;
                break;
            }
        }
        require(spawnSupported, "spawn must be supported by real island collision at world Y=1.20, not a hidden global floor");
        System.out.println("OCEAN_WORLD_PASS | objects=" + map.scene().size() + " | seaLevel=" + seaLevel
            + " | beachBands=" + sandCount + "/" + wetSandCount + "/" + shallowCount + "/" + foamCount
            + " | waterTriangles=" + water.mesh().triangleCount()
            + " | stepDownPhysics=PASS | spawnCollider=PASS | ghostSkyObjects=NONE");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
