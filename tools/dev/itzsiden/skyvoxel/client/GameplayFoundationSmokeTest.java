package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.entity.EntitySnapshot;
import dev.itzsiden.skyvoxel.client.entity.EntityModelLoader;
import dev.itzsiden.skyvoxel.engine.gameplay.EquipmentLoadout;
import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import dev.itzsiden.skyvoxel.engine.gameplay.PlayerProfileSnapshot;
import dev.itzsiden.skyvoxel.engine.physics.EntityRaycast;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.WindowConfig;

/** Dependency-free smoke test; run after compiling the main source sets. */
public final class GameplayFoundationSmokeTest {
    private static final class TestWindow implements RenderWindow {
        private boolean space;
        private boolean forward;
        @Override public void pollEvents() { }
        @Override public boolean shouldClose() { return false; }
        @Override public void requestClose() { }
        @Override public boolean isKeyDown(Key key) { return (key == Key.SPACE && space) || (key == Key.W && forward); }
        @Override public MouseDelta consumeMouseDelta() { return MouseDelta.ZERO; }
        @Override public void setMouseCaptured(boolean captured) { }
        @Override public int framebufferWidth() { return 1280; }
        @Override public int framebufferHeight() { return 720; }
    }

    public static void main(String[] args) {
        EquipmentLoadout kit = EquipmentLoadout.starterKit();
        PlayerStats stats = new PlayerStats(kit);
        check(stats.maxHealth() > 100, "armor should contribute max health");
        check(stats.maxMana() > 60, "accessory should contribute mana");
        check(stats.defense() > 0 && stats.strength() > 10 && stats.intelligence() > 0, "equipment/pet stats should aggregate");
        PlayerProfileSnapshot localProfile = PlayerProfileSnapshot.from("self", "You", 1, stats, true);
        check(localProfile.equippedItemIds().size() >= 7, "profile snapshot should carry equipment IDs");
        float hpBefore = stats.health();
        float taken = stats.takeDamage(20);
        check(taken > 0 && taken < 20 && stats.health() < hpBefore, "defense should mitigate damage");
        float manaBefore = stats.mana();
        check(stats.spendMana(10) && stats.mana() == manaBefore - 10, "mana spending");
        stats.tick(1);
        check(stats.mana() > manaBefore - 10, "mana regeneration");

        Mesh cube = new Mesh("test-cube", CuboidMeshFactory.createUnitCuboid());
        EntityModel model = new EntityModel("test-model", 1.0f,
            java.util.List.of(new EntityModel.Part("body", cube, new Material("test", 0.5f, 0.6f, 0.7f),
                0, 0.5f, 0, 0.8f, 1.0f, 0.8f, 0)));
        Entity target = new Entity("target", EntityType.NPC, model, "Target", "TEST NPC").setPosition(0, 0, -3);
        EntityWorld entities = new EntityWorld().add(target);
        StaticCollisionWorld emptyWorld = new StaticCollisionWorld(new Scene());
        Scene floorScene = new Scene();
        floorScene.add(new RenderObject("test-floor", cube, new Material("floor", 0.3f, 0.4f, 0.5f),
            new Transform().setPosition(0, -0.5f, 0).setScale(200, 1, 200)).setCollidable(true));
        StaticCollisionWorld floorWorld = new StaticCollisionWorld(floorScene);
        var rayHit = EntityRaycast.raycast(entities, emptyWorld, "self", 0, 0.5, 0, 0, 0, -1, 6);
        check(rayHit.isPresent() && rayHit.get().entity() == target, "model-derived entity hitbox raycast");
        EntityWorld blockerEntities = new EntityWorld().add(new Entity("blocker", EntityType.NPC, model, "Blocker", "TEST NPC")
            .setPosition(0, 0, -2));
        PlayerController blockedWalker = new PlayerController(floorWorld,
            new dev.itzsiden.skyvoxel.client.map.GameMap.Spawn(0, 0, 0, 0));
        blockedWalker.setDynamicColliders(() -> blockerEntities.entities().stream().map(EntityRaycast::hitbox).toList());
        TestWindow blockerWindow = new TestWindow();
        blockerWindow.forward = true;
        for (int i = 0; i < 180; i++) blockedWalker.update(blockerWindow, 1.0f / 60);
        blockerWindow.forward = false;
        check(blockedWalker.z() > -1.5 && blockedWalker.z() < -1.1, "player should collide with NPC hitbox instead of passing through");
        EntitySnapshot snapshot = new EntitySnapshot("replicated", EntityType.MOB, "test-model", "Replica", "LV. 1",
            1, 2, 3, 0.25f, 7, 10, 4);
        Entity replica = new EntityWorld().applySnapshot(snapshot, java.util.Map.of("test-model", model));
        check(replica.defense() == 4 && replica.health() == 7, "entity snapshots should replicate health and defense");

        TestWindow window = new TestWindow();
        PlayerController jumper = new PlayerController(floorWorld, new dev.itzsiden.skyvoxel.client.map.GameMap.Spawn(0, 0, 0, 0));
        double maxY = 0;
        window.space = true;
        jumper.update(window, 1.0f / 60);
        window.space = false;
        for (int i = 0; i < 150; i++) {
            jumper.update(window, 1.0f / 60);
            maxY = Math.max(maxY, jumper.feetY());
        }
        check(jumper.grounded() && Math.abs(jumper.feetY()) < 0.001, "jump should return to floor");
        check(maxY > 1.0 && maxY < 2.0, "jump height should be stable");
        check(jumper.consumeFallDamage() == 0, "normal jump must not cause fall damage");

        PlayerController faller = new PlayerController(floorWorld, new dev.itzsiden.skyvoxel.client.map.GameMap.Spawn(0, 8, 0, 0));
        for (int i = 0; i < 200; i++) faller.update(window, 1.0f / 60);
        check(faller.grounded() && faller.consumeFallDamage() > 0, "large fall should generate damage");

        Scene platformScene = new Scene();
        platformScene.add(new RenderObject("platform", cube, new Material("platform", 0.3f, 0.4f, 0.5f),
            new Transform().setPosition(0, 0.5f, 0).setScale(10, 1, 10)).setCollidable(true));
        StaticCollisionWorld platformWorld = new StaticCollisionWorld(platformScene);
        PlayerController platformDrop = new PlayerController(platformWorld,
            new dev.itzsiden.skyvoxel.client.map.GameMap.Spawn(0, 6, 0, 0));
        for (int i = 0; i < 240; i++) platformDrop.update(window, 1.0f / 60);
        check(platformDrop.grounded() && Math.abs(platformDrop.feetY() - 1.0) < 0.001,
            "swept vertical collision should land on raised platforms");
        check(platformDrop.consumeFallDamage() > 0, "falling from height onto platform should damage player");

        HostileMobSystem combat = new HostileMobSystem();
        Entity mob = new Entity("mob-test", EntityType.MOB, model, "Test Mob", "LV. 1")
            .setPosition(0, 0, -2).setHealth(10, 10).setDefense(0);
        entities.add(mob);
        combat.register(mob, 8, 1.2f, 2, 8, 1.0f, 0.3f, 1.0f);
        String attack = combat.playerAttack(mob, entities, stats);
        check(attack.startsWith("Defeated") && entities.get(mob.id()) == null, "player attack should defeat and remove mob");
        for (int i = 0; i < 21; i++) combat.update(0.05f, jumper, stats, entities, emptyWorld);
        check(entities.get(mob.id()) != null && mob.health() == mob.maxHealth(), "hostile mobs should respawn after cooldown");

        Entity attackerMob = new Entity("mob-attacker", EntityType.MOB, model, "Attacker", "LV. 1")
            .setPosition(0, 0, 0.4f).setHealth(10, 10);
        entities.add(attackerMob);
        combat.register(attackerMob, 8, 1.2f, 2, 8, 1.0f, 0.3f, 0);
        float healthBeforeMobAttack = stats.health();
        var combatEvents = combat.update(1.0f / 60, jumper, stats, entities, emptyWorld);
        check(!combatEvents.isEmpty() && stats.health() < healthBeforeMobAttack, "hostile mobs should attack in melee range");

        try {
            var loadedMap = dev.itzsiden.skyvoxel.client.map.MapLoader.load(java.nio.file.Path.of("data/maps/main_hub.vmap"));
            check(loadedMap.scene().size() > 0, "handcrafted hub map still loads");
            var loadedModel = new EntityModelLoader(cube).load(java.nio.file.Path.of("data/entities/models/sky_wisp.vmodel"));
            check(loadedModel.parts().size() >= 4, "authored .vmodel still loads");
        } catch (java.io.IOException exception) {
            throw new AssertionError("map/model asset regression", exception);
        }

        System.out.println("GAMEPLAY_FOUNDATION_PASS | maxHP=" + Math.round(stats.maxHealth())
            + " | defense=" + Math.round(stats.defense()) + " | jumpPeak=" + String.format(java.util.Locale.ROOT, "%.2f", maxY)
            + " | entityRaycast=" + String.format(java.util.Locale.ROOT, "%.2f", rayHit.get().distance()) + " | mobCombat=PASS | platformFall=PASS");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
