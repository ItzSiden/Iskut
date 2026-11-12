package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.client.map.MapLoader;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;

import java.nio.file.Path;
import java.util.List;

public final class MiningIslandTravelSmokeTest {
    public static void main(String[] args) throws Exception {
        Path mapPath = Path.of("data/maps/main_hub.vmap").toAbsolutePath().normalize();
        GameMap map = MapLoader.load(mapPath);
        check(map != null, "Map should load successfully");

        // Verify mining structures are in the scene
        boolean foundMiningTerrain = false;
        boolean foundMiningProps = false;
        boolean foundMiningDock = false;
        int miningObjectCount = 0;

        for (RenderObject obj : map.scene().objects()) {
            if (obj.id().startsWith("mine_terrain/")) {
                foundMiningTerrain = true;
                miningObjectCount++;
            } else if (obj.id().startsWith("mine_props/")) {
                foundMiningProps = true;
                miningObjectCount++;
            } else if (obj.id().startsWith("mine_dock/")) {
                foundMiningDock = true;
                miningObjectCount++;
            }
        }

        check(foundMiningTerrain, "mine_terrain objects must be placed in map scene");
        check(foundMiningProps, "mine_props objects must be placed in map scene");
        check(foundMiningDock, "mine_dock objects must be placed in map scene");
        check(miningObjectCount >= 250, "expected at least 250 mining island objects, got " + miningObjectCount);

        // Verify collision world includes mining island colliders
        StaticCollisionWorld collision = new StaticCollisionWorld(map.scene());
        check(collision.colliders().size() > 0, "colliders must be present");

        // Create player and ship exploration system
        EntityWorld entities = new EntityWorld();
        Mesh cube = new Mesh("test-cube", CuboidMeshFactory.createUnitCuboid());
        EntityModel model = new EntityModel("cutter-model", 1.0f,
            List.of(new EntityModel.Part("hull", cube, new Material("hull", 0.3f, 0.2f, 0.1f), 0, 0, 0, 1, 1, 1, 0)));
        ShipExplorationSystem shipExploration = new ShipExplorationSystem(entities, model, collision);
        PlayerController player = new PlayerController(collision, map.spawn());

        // 1. Summon and board at the hub harbor
        shipExploration.summonAndBoard(player);
        check(shipExploration.boarded(), "Ship should be boarded after summon");

        // 2. Test disembark at Mining Island dock (e.g. at (0, 180))
        IslandArchipelago.Landing mineDockLanding = IslandArchipelago.findLanding(0.0f, 180.0f);
        check(mineDockLanding != null, "Must find landing at Mining Island dock");
        check(Math.abs(mineDockLanding.surfaceY() - 1.18f) < 0.01f, "Dock landing Y must be 1.18");
        check(mineDockLanding.z() >= 175.0f && mineDockLanding.z() <= 194.0f, "Dock landing Z must be on the pier");

        // 3. Test disembark near Mining Island shore (e.g. at (15, 230))
        IslandArchipelago.Landing mineShoreLanding = IslandArchipelago.findLanding(15.0f, 230.0f);
        check(mineShoreLanding != null, "Must find landing at Mining Island shore");
        check(Math.abs(mineShoreLanding.surfaceY() - 1.18f) < 0.01f, "Shore landing Y must be 1.18");

        // 4. Test return landing at Hub dock (e.g. at (0, 66))
        IslandArchipelago.Landing hubDockLanding = IslandArchipelago.findLanding(0.0f, 66.0f);
        check(hubDockLanding != null, "Must find landing at Hub dock");
        check(Math.abs(hubDockLanding.surfaceY() - 1.18f) < 0.01f, "Hub landing Y must be 1.18");

        System.out.println("MINING_ISLAND_TRAVEL_SMOKE_PASS | objects=" + miningObjectCount
            + " | mineLanding=" + mineDockLanding.x() + "," + mineDockLanding.surfaceY() + "," + mineDockLanding.z()
            + " | hubLanding=" + hubDockLanding.x() + "," + hubDockLanding.surfaceY() + "," + hubDockLanding.z());
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
