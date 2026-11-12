package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.physics.AxisAlignedBox;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;

/** Small, game-thread sailing controller for one player-owned harbor cutter. */
final class ShipExplorationSystem {
    private static final String VESSEL_ID = "player-harbor-cutter";
    private final EntityWorld entities;
    private final EntityModel model;
    private final StaticCollisionWorld collision;

    private Entity vessel;
    private boolean boarded;
    private float x;
    private float y = 0.78f;
    private float z = 66.0f;
    private float yaw;
    private float pitch;
    private float speed;
    private float waveTime;

    ShipExplorationSystem(EntityWorld entities, EntityModel model, StaticCollisionWorld collision) {
        this.entities = java.util.Objects.requireNonNull(entities, "entities");
        this.model = java.util.Objects.requireNonNull(model, "model");
        this.collision = java.util.Objects.requireNonNull(collision, "collision");
    }

    boolean boarded() { return boarded; }
    float speed() { return speed; }

    void summonAndBoard(PlayerController player) {
        if (vessel == null) {
            vessel = new Entity(VESSEL_ID, EntityType.PET, model, "Harbor Cutter", "PLAYER VESSEL")
                .setShowNameplate(false).setShowHealthBar(false);
            entities.add(vessel);
        }
        x = 0.0f;
        y = 0.78f;
        z = 66.0f;
        yaw = (float) Math.PI;   // bow points to open sea (south), away from the pier
        pitch = 0.0f;
        speed = 0.0f;
        waveTime = 0.0f;
        boarded = true;
        syncVessel();
        placePlayerAtHelm(player);
    }

    boolean canBoard(PlayerController player) {
        if (boarded || vessel == null || !vessel.visible()) return false;
        double dx = player.x() - x;
        double dz = player.z() - z;
        return dx * dx + dz * dz <= 30.25;
    }

    void board(PlayerController player) {
        if (vessel == null || boarded) return;
        boarded = true;
        speed = 0.0f;
        yaw = -vessel.yaw();
        x = vessel.x();
        y = vessel.y();
        z = vessel.z();
        placePlayerAtHelm(player);
    }

    boolean disembark(PlayerController player) {
        if (!boarded) return false;
        IslandArchipelago.Landing landing = IslandArchipelago.findLanding(x, z);
        if (landing == null) return false;

        boarded = false;
        speed = 0.0f;
        player.setExternalPose(landing.x(), landing.surfaceY(), landing.z(), yaw, 0.0);
        return true;
    }

    void update(RenderWindow window, float deltaSeconds, PlayerController player) {
        if (!boarded || vessel == null) return;

        float dt = Math.max(0.0f, Math.min(deltaSeconds, 0.05f));
        MouseDelta mouse = window.consumeMouseDelta();
        yaw += mouse.x() * 0.0022f;
        pitch = clamp(pitch - (float) mouse.y() * 0.0020f, -0.95f, 0.95f);

        float throttle = (window.isKeyDown(Key.W) ? 1.0f : 0.0f)
            - (window.isKeyDown(Key.S) ? 1.0f : 0.0f);
        float steering = (window.isKeyDown(Key.D) ? 1.0f : 0.0f)
            - (window.isKeyDown(Key.A) ? 1.0f : 0.0f);

        if (throttle > 0.0f) speed = Math.min(8.0f, speed + 4.2f * dt);
        else if (throttle < 0.0f) speed = Math.max(-2.8f, speed - 4.0f * dt);
        else speed *= (float) Math.exp(-0.78f * dt);

        if (Math.abs(speed) > 0.06f) {
            yaw += steering * 1.25f * dt * (speed >= 0.0f ? 1.0f : -1.0f);
        }

        // Move per axis so the hull slides along shores/piers instead of sailing through them.
        float nx = clamp(x + (float) Math.sin(yaw) * speed * dt, -1800.0f, 1800.0f);
        float nz = clamp(z - (float) Math.cos(yaw) * speed * dt, -1800.0f, 1800.0f);
        boolean blocked = false;
        if (hullFree(nx, z)) x = nx; else blocked = true;
        if (hullFree(x, nz)) z = nz; else blocked = true;
        if (blocked) speed *= -0.2f;   // soft bump, never sticks

        waveTime += dt;
        y = 0.78f + (float) Math.sin(waveTime * 1.65f) * 0.035f;
        syncVessel();
        placePlayerAtHelm(player);
    }

    /** Hull = two boxes (bow + stern) at water level, checked against islands, cliffs and the pier. */
    private boolean hullFree(float cx, float cz) {
        float fx = (float) Math.sin(yaw), fz = -(float) Math.cos(yaw);
        for (float off : new float[] {-1.9f, 1.9f}) {
            float px = cx + fx * off, pz = cz + fz * off;
            if (collision.intersects(new AxisAlignedBox(px - 1.35, 1.06, pz - 1.35, px + 1.35, 1.9, pz + 1.35))) return false;
        }
        return true;
    }

    private void syncVessel() {
        vessel.setPosition(x, y, z).setYaw(-yaw);   // entity yaw is mirrored vs camera yaw
    }

    private void placePlayerAtHelm(PlayerController player) {
        // The deck is authored 0.78 units above the vessel origin.
        player.setExternalPose(x, y + 0.80f, z, yaw, pitch);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
