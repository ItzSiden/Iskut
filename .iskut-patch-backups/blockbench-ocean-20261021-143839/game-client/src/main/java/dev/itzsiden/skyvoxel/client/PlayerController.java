package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.physics.AxisAlignedBox;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.client.map.GameMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;

/** First-person movement with AABB collision, coyote time, jump buffering, crouch and sprint. */
final class PlayerController {
    private static final double HALF_WIDTH = 0.30;
    private static final double STANDING_HEIGHT = 1.50;
    private static final double CROUCH_HEIGHT = 1.10;
    private static final double WALK_SPEED = 4.5;
    private static final double SPRINT_SPEED = 5.7;
    private static final double CROUCH_SPEED = 1.3;
    /** MC step height: slab/stairs walk up, full block needs a jump. */
    private static final double AUTO_STEP_HEIGHT = 0.6;
    private static final double GRAVITY = 32.0;
    private static final double JUMP_SPEED = 8.94;      // apex ~1.25 blocks, ~0.56s air time like MC
    private static final double TERMINAL_VELOCITY = 78.4;
    private static final double COYOTE_SECONDS = 0.11;
    private static final double JUMP_BUFFER_SECONDS = 0.14;
    private static final double EPSILON = 1.0e-5;

    private final StaticCollisionWorld collisionWorld;
    private Supplier<List<AxisAlignedBox>> dynamicColliders = List::of;
    private final double spawnX, spawnY, spawnZ, spawnYaw;
    private double x = 0.0;
    private double feetY = 0.0;
    private double z = 8.0;
    private double yaw;
    private double pitch;
    private double velocityX;
    private double velocityZ;
    private double verticalVelocity;
    private boolean grounded = true;
    private boolean previousSpaceDown;
    private boolean crouching;
    private boolean sprinting;
    private boolean moving;
    private double coyoteRemaining = COYOTE_SECONDS;
    private double jumpBufferRemaining;
    private double horizontalSpeed;
    private double bobPhase;
    private double cameraBob;
    private double landingDip;
    private double stepCameraLag;
    private double peakAirY;
    private float pendingFallDamage;
    private double movementMultiplier = 1.0;
    private boolean inputEnabled = true;

    PlayerController(StaticCollisionWorld collisionWorld) {
        this(collisionWorld, new GameMap.Spawn(0.0f, 0.0f, 8.0f, 0.0f));
    }

    PlayerController(StaticCollisionWorld collisionWorld, GameMap.Spawn spawn) {
        this.collisionWorld = collisionWorld;
        this.spawnX = spawn.x();
        this.spawnY = spawn.y();
        this.spawnZ = spawn.z();
        this.spawnYaw = spawn.yawRadians();
        this.x = spawnX;
        this.feetY = spawnY;
        this.z = spawnZ;
        this.yaw = spawnYaw;
        this.peakAirY = spawnY;
    }

    void update(RenderWindow window, float deltaSeconds) {
        // Simulate collision/gravity in short substeps so low FPS cannot tunnel through thin floors.
        double frameDt = Math.max(0.0, Math.min(deltaSeconds, 0.10));
        MouseDelta mouse = window.consumeMouseDelta();
        if (!inputEnabled) {
            velocityX = velocityZ = verticalVelocity = 0.0;
            cameraBob *= Math.exp(-frameDt * 9.0);
            return;
        }
        yaw += mouse.x() * 0.0022;
        pitch -= mouse.y() * 0.0022;
        double pitchLimit = Math.toRadians(89.0);
        pitch = Math.max(-pitchLimit, Math.min(pitchLimit, pitch));
        if (frameDt <= 0.0) return;
        int steps = Math.max(1, (int) Math.ceil(frameDt / (1.0 / 120.0)));
        double stepDt = frameDt / steps;
        for (int i = 0; i < steps; i++) updatePhysicsStep(window, stepDt);
    }

    private void updatePhysicsStep(RenderWindow window, double dt) {
        if (grounded) peakAirY = feetY;
        landingDip *= Math.exp(-dt * 14.0);
        stepCameraLag *= Math.exp(-dt * 13.0);
        boolean airborneAtFrameStart = !grounded;
        boolean spaceDown = window.isKeyDown(Key.SPACE);
        if (spaceDown && !previousSpaceDown) jumpBufferRemaining = JUMP_BUFFER_SECONDS;
        else jumpBufferRemaining = Math.max(0.0, jumpBufferRemaining - dt);
        previousSpaceDown = spaceDown;

        if (grounded) coyoteRemaining = COYOTE_SECONDS;
        else coyoteRemaining = Math.max(0.0, coyoteRemaining - dt);

        boolean wantsCrouch = window.isKeyDown(Key.LEFT_CONTROL);
        if (wantsCrouch) {
            crouching = true;
        } else if (crouching && !collidesAt(x, feetY, z, STANDING_HEIGHT)) {
            crouching = false;
        }
        double bodyHeight = bodyHeight();

        double forwardInput = (window.isKeyDown(Key.W) ? 1.0 : 0.0)
            - (window.isKeyDown(Key.S) ? 1.0 : 0.0);
        double rightInput = (window.isKeyDown(Key.D) ? 1.0 : 0.0)
            - (window.isKeyDown(Key.A) ? 1.0 : 0.0);
        double inputLength = Math.sqrt(forwardInput * forwardInput + rightInput * rightInput);
        moving = inputLength > 0.001;
        if (inputLength > 1.0) {
            forwardInput /= inputLength;
            rightInput /= inputLength;
        }

        sprinting = window.isKeyDown(Key.LEFT_SHIFT) && moving && !crouching;
        double speed = (crouching ? CROUCH_SPEED : (sprinting ? SPRINT_SPEED : WALK_SPEED)) * movementMultiplier;
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double dirX = sinYaw * forwardInput + cosYaw * rightInput;
        double dirZ = -cosYaw * forwardInput + sinYaw * rightInput;
        double desiredX = dirX * speed;
        double desiredZ = dirZ * speed;
        if (grounded) {
            double k = 1.0 - Math.exp(-12.0 * dt);
            velocityX += ((moving ? desiredX : 0.0) - velocityX) * k;
            velocityZ += ((moving ? desiredZ : 0.0) - velocityZ) * k;
        } else {
            double drag = Math.exp(-1.9 * dt);
            double airAcc = 8.0 * (sprinting ? 1.3 : 1.0) * dt;
            velocityX = velocityX * drag + (moving ? dirX * airAcc : 0.0);
            velocityZ = velocityZ * drag + (moving ? dirZ * airAcc : 0.0);
        }

        if (jumpBufferRemaining > 0.0 && (grounded || coyoteRemaining > 0.0)) jump();

        boolean wasGrounded = grounded;
        boolean wasAirborneBeforeVerticalStep = !grounded;
        double impactVelocity = verticalVelocity;
        verticalVelocity = Math.max(verticalVelocity - GRAVITY * dt, -TERMINAL_VELOCITY);
        // There is deliberately no implicit y=0 floor: only real colliders can support the player.
        moveBody(velocityX * dt, verticalVelocity * dt, velocityZ * dt, bodyHeight, wasGrounded);
        if (grounded) coyoteRemaining = COYOTE_SECONDS;

        boolean landedThisFrame = wasAirborneBeforeVerticalStep && grounded;
        if (landedThisFrame) {
            // Tiny impact response gives landings weight without an intrusive camera shake.
            landingDip = Math.min(0.105, Math.max(0.0, Math.abs(impactVelocity) - 2.0) * 0.0105);
        }
        if (!grounded) {
            peakAirY = Math.max(peakAirY, feetY);
        } else {
            if (landedThisFrame || airborneAtFrameStart) {
                double fallDistance = Math.max(0.0, peakAirY - feetY);
                if (fallDistance > 3.0) pendingFallDamage += (float) Math.ceil((fallDistance - 3.0) * 5.0);
            }
            peakAirY = feetY;
        }

        // Jump buffering: if Space was tapped just before a landing, launch on the landing frame.
        if (grounded && jumpBufferRemaining > 0.0) jump();

        horizontalSpeed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (grounded && moving) {
            bobPhase += horizontalSpeed * dt * (sprinting ? 1.22 : 1.0);
            double amplitude = sprinting ? 0.042 : 0.022;
            if (crouching) amplitude *= 0.45;
            cameraBob = Math.sin(bobPhase * 2.0) * amplitude;
        } else {
            cameraBob *= Math.max(0.0, 1.0 - dt * 9.0);
        }
    }

    private void jump() {
        verticalVelocity = JUMP_SPEED;
        grounded = false;
        coyoteRemaining = 0.0;
        jumpBufferRemaining = 0.0;
    }

    private static final double SNAP_DOWN = 0.2;

    private static boolean outside(AxisAlignedBox b, double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        return b.maxX() < minX || b.minX() > maxX || b.maxY() < minY || b.minY() > maxY
            || b.maxZ() < minZ || b.minZ() > maxZ;
    }

    private List<AxisAlignedBox> obstacles(double h, double reachXZ, double reachY) {
        double minX = x - HALF_WIDTH - reachXZ, maxX = x + HALF_WIDTH + reachXZ;
        double minZ = z - HALF_WIDTH - reachXZ, maxZ = z + HALF_WIDTH + reachXZ;
        double minY = feetY - reachY, maxY = feetY + h + reachY;
        List<AxisAlignedBox> out = new ArrayList<>();
        for (StaticCollisionWorld.Collider c : collisionWorld.colliders()) {
            AxisAlignedBox b = c.bounds();
            if (!outside(b, minX, minY, minZ, maxX, maxY, maxZ)) out.add(b);
        }
        for (AxisAlignedBox b : dynamicColliders.get()) {
            if (!outside(b, minX, minY, minZ, maxX, maxY, maxZ)) out.add(b);
        }
        return out;
    }

    private static double clipY(List<AxisAlignedBox> obs, double px, double py, double pz, double h, double dy) {
        for (AxisAlignedBox b : obs) {
            if (px + HALF_WIDTH <= b.minX() || px - HALF_WIDTH >= b.maxX()
                || pz + HALF_WIDTH <= b.minZ() || pz - HALF_WIDTH >= b.maxZ()) continue;
            if (dy < 0.0 && py >= b.maxY() - EPSILON) dy = Math.max(dy, b.maxY() - py);
            else if (dy > 0.0 && py + h <= b.minY() + EPSILON) dy = Math.min(dy, b.minY() - (py + h));
        }
        return dy;
    }

    private static double clipX(List<AxisAlignedBox> obs, double px, double py, double pz, double h, double dx) {
        for (AxisAlignedBox b : obs) {
            if (py + h <= b.minY() + EPSILON || py >= b.maxY() - EPSILON
                || pz + HALF_WIDTH <= b.minZ() || pz - HALF_WIDTH >= b.maxZ()) continue;
            if (dx > 0.0 && px + HALF_WIDTH <= b.minX() + EPSILON) dx = Math.min(dx, b.minX() - (px + HALF_WIDTH));
            else if (dx < 0.0 && px - HALF_WIDTH >= b.maxX() - EPSILON) dx = Math.max(dx, b.maxX() - (px - HALF_WIDTH));
        }
        return dx;
    }

    private static double clipZ(List<AxisAlignedBox> obs, double px, double py, double pz, double h, double dz) {
        for (AxisAlignedBox b : obs) {
            if (py + h <= b.minY() + EPSILON || py >= b.maxY() - EPSILON
                || px + HALF_WIDTH <= b.minX() || px - HALF_WIDTH >= b.maxX()) continue;
            if (dz > 0.0 && pz + HALF_WIDTH <= b.minZ() + EPSILON) dz = Math.min(dz, b.minZ() - (pz + HALF_WIDTH));
            else if (dz < 0.0 && pz - HALF_WIDTH >= b.maxZ() - EPSILON) dz = Math.max(dz, b.maxZ() - (pz - HALF_WIDTH));
        }
        return dz;
    }

    /** Sneak edge check: is there floor within one step height under this position? */
    private boolean supported(List<AxisAlignedBox> obs, double px, double pz) {
        double lo = feetY - AUTO_STEP_HEIGHT, hi = feetY + 0.001;
        for (AxisAlignedBox b : obs) {
            if (px + HALF_WIDTH > b.minX() && px - HALF_WIDTH < b.maxX()
                && pz + HALF_WIDTH > b.minZ() && pz - HALF_WIDTH < b.maxZ()
                && hi > b.minY() && lo < b.maxY()) return true;
        }
        return false;
    }

    private static double shrink(double d) {
        if (Math.abs(d) < 0.05) return 0.0;
        return d > 0.0 ? d - 0.05 : d + 0.05;
    }

    private void moveBody(double dx, double dy, double dz, double h, boolean wasGrounded) {
        List<AxisAlignedBox> obs = obstacles(h, 0.5 + Math.max(Math.abs(dx), Math.abs(dz)),
            AUTO_STEP_HEIGHT + SNAP_DOWN + 0.5 + Math.abs(dy));

        if (crouching && wasGrounded) {
            while (dx != 0.0 && !supported(obs, x + dx, z)) dx = shrink(dx);
            while (dz != 0.0 && !supported(obs, x, z + dz)) dz = shrink(dz);
            while (dx != 0.0 && dz != 0.0 && !supported(obs, x + dx, z + dz)) {
                dx = shrink(dx);
                dz = shrink(dz);
            }
        }

        double cy = clipY(obs, x, feetY, z, h, dy);
        double ny = feetY + cy;
        double cx = clipX(obs, x, ny, z, h, dx);
        double nx = x + cx;
        double cz = clipZ(obs, nx, ny, z, h, dz);
        double nz = z + cz;

        boolean blocked = cx != dx || cz != dz;
        boolean hitGround = dy < 0.0 && cy != dy;
        boolean hitHead = dy > 0.0 && cy != dy;

        if (blocked && (wasGrounded || hitGround) && verticalVelocity <= 0.0) {
            double up = clipY(obs, x, feetY, z, h, AUTO_STEP_HEIGHT);
            double sy = feetY + up;
            double sx = clipX(obs, x, sy, z, h, dx);
            double spx = x + sx;
            double sz = clipZ(obs, spx, sy, z, h, dz);
            double spz = z + sz;
            double down = clipY(obs, spx, sy, spz, h, -up);
            if (sx * sx + sz * sz > cx * cx + cz * cz + 1.0e-9) {
                double rise = sy + down - feetY;
                x = spx;
                z = spz;
                feetY = sy + down;
                if (rise > 0.01) stepCameraLag = Math.min(0.6, stepCameraLag + rise);
                grounded = down > -up + 1.0e-9;
                if (grounded) verticalVelocity = 0.0;
                if (sx != dx) velocityX = 0.0;
                if (sz != dz) velocityZ = 0.0;
                return;
            }
        }

        x = nx;
        feetY = ny;
        z = nz;
        if (cx != dx) velocityX = 0.0;
        if (cz != dz) velocityZ = 0.0;
        if (hitGround) {
            grounded = true;
            verticalVelocity = 0.0;
        } else {
            grounded = false;
            if (hitHead) verticalVelocity = 0.0;
        }

        if (wasGrounded && !grounded && verticalVelocity <= 0.0) {
            double snap = clipY(obs, x, feetY, z, h, -SNAP_DOWN);
            if (snap > -SNAP_DOWN + 1.0e-9) {
                feetY += snap;
                grounded = true;
                verticalVelocity = 0.0;
            }
        }
    }

    private boolean collidesAt(double px, double py, double pz, double height) {
        AxisAlignedBox body = bodyBox(px, py, pz, height);
        if (collisionWorld.intersects(body)) return true;
        for (AxisAlignedBox collider : dynamicColliders.get()) if (body.intersects(collider)) return true;
        return false;
    }

    void setDynamicColliders(Supplier<List<AxisAlignedBox>> supplier) {
        dynamicColliders = java.util.Objects.requireNonNull(supplier, "supplier");
    }

    private static AxisAlignedBox bodyBox(double px, double py, double pz, double height) {
        return new AxisAlignedBox(px - HALF_WIDTH, py, pz - HALF_WIDTH,
            px + HALF_WIDTH, py + height, pz + HALF_WIDTH);
    }

    private double bodyHeight() { return crouching ? CROUCH_HEIGHT : STANDING_HEIGHT; }
    double x() { return x; }
    double feetY() { return feetY; }
    double z() { return z; }
    double yaw() { return yaw; }
    double pitch() { return pitch; }
    double eyeHeight() { return crouching ? 1.08 : 1.62; }
    /** Head bob plus a short, damped landing dip, in world-space units. */
    double cameraBob() { return cameraBob - landingDip - stepCameraLag; }
    boolean sprinting() { return sprinting; }
    boolean moving() { return moving; }
    boolean grounded() { return grounded; }
    boolean crouching() { return crouching; }
    double horizontalSpeed() { return horizontalSpeed; }
    double verticalVelocity() { return verticalVelocity; }
    double fallPeakY() { return peakAirY; }
    /** When below the shallow shoreline the player is lost at sea; the client starts its respawn flow. */
    boolean belowOceanRespawnPlane() { return feetY < -1.65; }

    float consumeFallDamage() {
        float damage = pendingFallDamage;
        pendingFallDamage = 0;
        return damage;
    }

    void setMovementMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier)) throw new IllegalArgumentException("Movement multiplier must be finite.");
        movementMultiplier = Math.max(0.5, Math.min(2.5, multiplier));
    }

    void setInputEnabled(boolean enabled) { inputEnabled = enabled; }

    void teleportToSpawn() {
        x = spawnX; feetY = spawnY; z = spawnZ; yaw = spawnYaw; pitch = 0;
        velocityX = velocityZ = verticalVelocity = 0;
        grounded = true; crouching = sprinting = moving = false;
        coyoteRemaining = COYOTE_SECONDS; jumpBufferRemaining = 0;
        peakAirY = feetY; pendingFallDamage = 0; cameraBob = 0; landingDip = 0; stepCameraLag = 0;
    }
}
