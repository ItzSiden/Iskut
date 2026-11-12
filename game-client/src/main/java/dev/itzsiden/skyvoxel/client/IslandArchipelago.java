package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

/**
 * Lightweight handcrafted sailing destinations for the hub ocean.
 * Each island is added to the collision scene and the render scene.
 */
final class IslandArchipelago {
    record Landing(float x, float surfaceY, float z) { }

    private record Island(String id, float x, float z, float width, float depth, float topY) { }

    private static final Island[] ISLANDS = {
        new Island("mosslight_key", 0.0f, -150.0f, 42.0f, 38.0f, 4.5f),
        new Island("coral_reach", 145.0f, -245.0f, 36.0f, 42.0f, 4.8f),
        new Island("frostglass_isle", -155.0f, -350.0f, 38.0f, 34.0f, 5.2f)
    };

    private IslandArchipelago() { }

    static void populate(Scene world, Scene renderWorld) {
        Mesh cube = new Mesh("archipelago-shared-cuboid", CuboidMeshFactory.createUnitCuboid());
        Material cliff = new Material("archipelago_cliff", 0.27f, 0.34f, 0.39f);
        Material darkStone = new Material("archipelago_dark_stone", 0.18f, 0.24f, 0.29f);
        Material grass = new Material("archipelago_grass", 0.24f, 0.52f, 0.29f);
        Material moss = new Material("archipelago_moss", 0.14f, 0.39f, 0.24f);
        Material sand = new Material("archipelago_sand", 0.72f, 0.62f, 0.42f);
        Material wood = new Material("archipelago_wood", 0.38f, 0.24f, 0.15f);
        Material leaves = new Material("archipelago_leaves", 0.14f, 0.43f, 0.24f);
        Material rock = new Material("archipelago_rock", 0.45f, 0.49f, 0.52f);

        for (Island island : ISLANDS) {
            String id = island.id();
            float foundationHeight = island.topY() + 1.0f;
            float foundationY = (island.topY() - 1.0f) * 0.5f;

            block(world, renderWorld, cube, id + "/foundation", cliff,
                island.x(), foundationY, island.z(), island.width(), foundationHeight, island.depth());
            block(world, renderWorld, cube, id + "/lower-rim", darkStone,
                island.x(), island.topY() - 0.72f, island.z() + 1.0f,
                island.width() + 1.4f, 0.55f, island.depth() - 1.0f);
            block(world, renderWorld, cube, id + "/shoreline", sand,
                island.x(), island.topY() - 0.42f, island.z(),
                island.width() - 1.4f, 0.28f, island.depth() - 1.4f);
            block(world, renderWorld, cube, id + "/grass-top", grass,
                island.x(), island.topY() - 0.14f, island.z(),
                island.width() - 2.2f, 0.28f, island.depth() - 2.2f);

            // Offset raised patches break the otherwise rectangular silhouette.
            block(world, renderWorld, cube, id + "/west-shelf", moss,
                island.x() - island.width() * 0.34f, island.topY() - 0.05f,
                island.z() + island.depth() * 0.22f, island.width() * 0.28f, 0.18f, island.depth() * 0.30f);
            block(world, renderWorld, cube, id + "/east-shelf", grass,
                island.x() + island.width() * 0.32f, island.topY() - 0.06f,
                island.z() - island.depth() * 0.24f, island.width() * 0.25f, 0.16f, island.depth() * 0.28f);

            // Small, low-poly landmarks make the destinations readable from the water.
            block(world, renderWorld, cube, id + "/marker-stone", rock,
                island.x() - 5.0f, island.topY() + 0.70f, island.z() - 4.0f, 1.6f, 1.4f, 1.5f);
            block(world, renderWorld, cube, id + "/marker-cap", sand,
                island.x() - 5.0f, island.topY() + 1.48f, island.z() - 4.0f, 1.15f, 0.18f, 1.1f);

            // Two blocky palms per island.
            palm(world, renderWorld, cube, id + "/palm-a", wood, leaves,
                island.x() + 7.0f, island.topY(), island.z() + 5.0f);
            palm(world, renderWorld, cube, id + "/palm-b", wood, leaves,
                island.x() - 9.0f, island.topY(), island.z() - 6.0f);
        }
    }

    static Landing findLanding(float x, float z) {
        // Safe return point on the harbor pier.
        if (Math.abs(x) <= 12.0f && z >= 47.0f && z <= 70.0f) {
            return new Landing(clamp(x + 3.0f, -8.8f, 8.8f), 1.18f, clamp(z, 52.0f, 61.0f));
        }

        // Landing point at the Mining Island dock / shore.
        if (Math.abs(x) <= 14.0f && z >= 165.0f && z <= 198.0f) {
            return new Landing(clamp(x + 2.5f, -2.5f, 2.5f), 1.18f, clamp(z, 175.0f, 194.0f));
        }
        if (Math.abs(x) <= 38.0f && z >= 170.0f && z <= 265.0f) {
            return new Landing(0.0f, 1.18f, 192.0f);
        }

        for (Island island : ISLANDS) {
            float halfW = island.width() * 0.5f - 4.0f;
            float halfD = island.depth() * 0.5f - 4.0f;
            if (Math.abs(x - island.x()) <= halfW && Math.abs(z - island.z()) <= halfD) {
                float landingX = clamp(x + 3.0f,
                    island.x() - island.width() * 0.5f + 2.5f,
                    island.x() + island.width() * 0.5f - 2.5f);
                float landingZ = clamp(z,
                    island.z() - island.depth() * 0.5f + 2.5f,
                    island.z() + island.depth() * 0.5f - 2.5f);
                return new Landing(landingX, island.topY(), landingZ);
            }
        }
        return null;
    }

    private static void palm(Scene world, Scene renderWorld, Mesh cube, String id,
                             Material wood, Material leaves, float x, float topY, float z) {
        block(world, renderWorld, cube, id + "/trunk", wood, x, topY + 1.05f, z, 0.48f, 2.1f, 0.48f);
        block(world, renderWorld, cube, id + "/crown", leaves, x, topY + 2.30f, z, 2.0f, 1.05f, 1.8f);
        block(world, renderWorld, cube, id + "/crown-top", leaves, x, topY + 2.92f, z, 1.25f, 0.45f, 1.2f);
    }

    private static void block(Scene world, Scene renderWorld, Mesh cube, String id, Material material,
                              float x, float y, float z, float width, float height, float depth) {
        RenderObject object = new RenderObject(id, cube, material,
            new Transform().setPosition(x, y, z).setScale(width, height, depth)).setCollidable(true);
        world.add(object);
        if (renderWorld != world) renderWorld.add(object);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
