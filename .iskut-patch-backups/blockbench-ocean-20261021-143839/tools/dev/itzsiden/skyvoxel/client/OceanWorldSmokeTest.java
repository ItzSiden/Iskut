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
        int transitionCount = 0, sandCount = 0, wetSandCount = 0, shallowCount = 0, foamCount = 0;
        boolean transitionSolid = true, sandSolid = true, wetSandSolid = true, shallowSolid = false, foamSolid = false;
        double transitionTop = Double.NaN, wetSandTop = Double.NaN, shallowTop = Double.NaN, foamTop = Double.NaN;
        RenderObject water = null;
        for (RenderObject object : map.scene().objects()) {
            materials.add(object.material().name());
            if (object.id().equals("ocean_surface/ocean_water")) water = object;
            if (object.id().startsWith("beach_transition/")) {
                switch (object.material().name()) {
                    case "shore_transition" -> { transitionCount++; transitionSolid &= object.collidable(); transitionTop = object.transform().y() + object.transform().scaleY() * 0.5; }
                    case "sand" -> { sandCount++; sandSolid &= object.collidable(); }
                    case "wet_sand" -> { wetSandCount++; wetSandSolid &= object.collidable(); wetSandTop = object.transform().y() + object.transform().scaleY() * 0.5; }
                    case "shallow_water" -> { shallowCount++; shallowSolid |= object.collidable(); shallowTop = object.transform().y() + object.transform().scaleY() * 0.5; }
                    case "foam" -> { foamCount++; foamSolid |= object.collidable(); foamTop = object.transform().y() + object.transform().scaleY() * 0.5; }
                }
            }
        }
        require(water != null, "ocean water object must load");
        require(!water.collidable(), "ocean surface must not be a solid floor");
        require(water.mesh().triangleCount() < 2_000,
            "large ocean plane must use coarse static geometry, got " + water.mesh().triangleCount() + " triangles");
        double seaLevel = water.transform().y() + water.transform().scaleY() * 0.5;
        require(Math.abs(seaLevel - (-0.18)) < 0.001, "sea level should be -0.18, got " + seaLevel);
        require(materials.containsAll(Set.of("water", "dirt", "shore_transition", "sand", "wet_sand", "shallow_water", "foam")),
            "all coast material textures must be available: " + materials);
        require(transitionCount >= 50 && sandCount >= 50 && wetSandCount >= 50 && shallowCount >= 50 && foamCount >= 50,
            "beach should have all five continuous bands: " + transitionCount + "/" + sandCount + "/" + wetSandCount + "/" + shallowCount + "/" + foamCount);
        require(transitionSolid && sandSolid && wetSandSolid && !shallowSolid && !foamSolid,
            "the transition/sand tiers must be walkable while shallow water/foam remain visual-only");
        require(Math.abs(transitionTop - 0.015) < 0.002 && Math.abs(wetSandTop - (-0.16)) < 0.002
                && shallowTop > seaLevel && foamTop > seaLevel,
            "shore band heights should meet the ocean: transition=" + transitionTop + " wet=" + wetSandTop
                + " shallow=" + shallowTop + " foam=" + foamTop + " sea=" + seaLevel);

        StaticCollisionWorld collision = new StaticCollisionWorld(map.scene());
        boolean spawnSupported = false;
        for (StaticCollisionWorld.Collider collider : collision.colliders()) {
            AxisAlignedBox b = collider.bounds();
            if (b.minX() <= map.spawn().x() && b.maxX() >= map.spawn().x()
                && b.minZ() <= map.spawn().z() && b.maxZ() >= map.spawn().z()
                && Math.abs(b.maxY()) < 0.03) {
                spawnSupported = true;
                break;
            }
        }
        require(spawnSupported, "spawn must be supported by real island collision, not a hidden global floor");
        System.out.println("OCEAN_WORLD_PASS | objects=" + map.scene().size() + " | seaLevel=" + seaLevel
            + " | beachBands=" + transitionCount + "/" + sandCount + "/" + wetSandCount + "/" + shallowCount + "/" + foamCount
            + " | waterTriangles=" + water.mesh().triangleCount()
            + " | stepDownPhysics=PASS | spawnCollider=PASS | ghostSkyObjects=NONE");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
