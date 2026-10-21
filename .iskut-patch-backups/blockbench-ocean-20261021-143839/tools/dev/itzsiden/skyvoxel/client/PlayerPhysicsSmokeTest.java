package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.MousePosition;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;

import java.util.EnumSet;
import java.util.Set;

/** Headless regression tests for low-step traversal, tall walls, and ceiling clearance. */
public final class PlayerPhysicsSmokeTest {
    private PlayerPhysicsSmokeTest() { }

    public static void main(String[] args) {
        Result stepSmall = traverseStep(0.2, Double.POSITIVE_INFINITY);
        require(stepSmall.feetY() > 0.15 && stepSmall.feetY() < 0.25 && stepSmall.z() < -1.25,
            "0.2 step should be climbed and traversed, result=" + stepSmall);
        Result stepHalf = traverseStep(0.5, Double.POSITIVE_INFINITY);
        require(stepHalf.feetY() > 0.43 && stepHalf.feetY() < 0.56 && stepHalf.z() < -1.25,
            "0.5 step should be climbed and traversed, result=" + stepHalf);
        Result stepOne = traverseStep(1.0, Double.POSITIVE_INFINITY);
        require(stepOne.feetY() > 0.90 && stepOne.feetY() < 1.10 && stepOne.z() < -1.25,
            "1.0 step should be climbed and traversed, result=" + stepOne);
        Result tallWall = traverseStep(1.4, Double.POSITIVE_INFINITY);
        require(tallWall.feetY() < 0.10 && tallWall.z() > -1.05,
            "1.4 wall must block player, result=" + tallWall);
        Result lowCeiling = traverseStep(0.8, 1.95);
        require(lowCeiling.feetY() < 0.10 && lowCeiling.z() > -1.05,
            "step must be rejected when the ceiling blocks standing clearance, result=" + lowCeiling);
        Result fellOff = fallOffPlatform();
        require(fellOff.feetY() < -1.0 && !fellOff.grounded() && fellOff.belowOcean(),
            "player must fall below the sea-respawn plane after leaving the only real platform, result=" + fellOff);
        System.out.println("PLAYER_PHYSICS_PASS | step0.2=" + stepSmall + " | step0.5=" + stepHalf
            + " | step1.0=" + stepOne + " | tallWall=BLOCKED | lowCeiling=BLOCKED | platformEdgeFall=" + fellOff);
    }

    private record Result(double feetY, double z, boolean grounded, boolean belowOcean) { }

    private static Result traverseStep(double stepHeight, double ceilingBottom) {
        Scene scene = new Scene();
        Mesh cube = new Mesh("physics-test-cube", CuboidMeshFactory.createUnitCuboid());
        addBox(scene, cube, "floor", 0.0f, -0.5f, 0.0f, 100.0f, 1.0f, 100.0f);
        addBox(scene, cube, "step", 0.0f, (float) (stepHeight * 0.5), -3.0f,
            2.0f, (float) stepHeight, 4.0f);
        if (Double.isFinite(ceilingBottom)) {
            addBox(scene, cube, "ceiling", 0.0f, (float) (ceilingBottom + 0.20), -1.5f,
                2.0f, 0.40f, 3.0f);
        }
        StaticCollisionWorld world = new StaticCollisionWorld(scene);
        PlayerController player = new PlayerController(world, new GameMap.Spawn(0, 0, 0, 0));
        FakeWindow window = new FakeWindow();
        window.down.add(Key.W);
        for (int frame = 0; frame < 40; frame++) player.update(window, 1.0f / 60.0f);
        return new Result(player.feetY(), player.z(), player.grounded(), player.belowOceanRespawnPlane());
    }

    private static Result fallOffPlatform() {
        Scene scene = new Scene();
        Mesh cube = new Mesh("physics-fall-test-cube", CuboidMeshFactory.createUnitCuboid());
        // This is the only support. There must be no hidden global Y=0 plane.
        addBox(scene, cube, "small-platform", 0.0f, -0.25f, 0.0f, 4.0f, 0.5f, 4.0f);
        StaticCollisionWorld world = new StaticCollisionWorld(scene);
        PlayerController player = new PlayerController(world, new GameMap.Spawn(0, 0, 0, 0));
        FakeWindow window = new FakeWindow();
        window.down.add(Key.W);
        for (int frame = 0; frame < 100; frame++) player.update(window, 1.0f / 60.0f);
        return new Result(player.feetY(), player.z(), player.grounded(), player.belowOceanRespawnPlane());
    }

    private static void addBox(Scene scene, Mesh mesh, String id,
                               float x, float y, float z, float width, float height, float depth) {
        scene.add(new RenderObject(id, mesh, Material.WHITE,
            new Transform().setPosition(x, y, z).setScale(width, height, depth)).setCollidable(true));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeWindow implements RenderWindow {
        private final Set<Key> down = EnumSet.noneOf(Key.class);
        @Override public void pollEvents() { }
        @Override public boolean shouldClose() { return false; }
        @Override public void requestClose() { }
        @Override public boolean isKeyDown(Key key) { return down.contains(key); }
        @Override public MouseDelta consumeMouseDelta() { return MouseDelta.ZERO; }
        @Override public void setMouseCaptured(boolean captured) { }
        @Override public int framebufferWidth() { return 640; }
        @Override public int framebufferHeight() { return 360; }
        @Override public MousePosition cursorPosition() { return MousePosition.INVALID; }
    }
}
