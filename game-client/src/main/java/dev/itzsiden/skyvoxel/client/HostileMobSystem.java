package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import dev.itzsiden.skyvoxel.engine.physics.AxisAlignedBox;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Small server-friendly hostile-mob loop: aggro, chase, melee cooldown, damage and timed respawn. */
final class HostileMobSystem {
    private record Profile(float aggroRange, float attackRange, float moveSpeed,
                           float attackDamage, float attackCooldown, float hitboxRadius,
                           float respawnSeconds) {
        Profile {
            if (!Float.isFinite(aggroRange) || aggroRange < 0 || !Float.isFinite(attackRange) || attackRange <= 0
                || !Float.isFinite(moveSpeed) || moveSpeed < 0 || !Float.isFinite(attackDamage) || attackDamage < 0
                || !Float.isFinite(attackCooldown) || attackCooldown <= 0 || !Float.isFinite(hitboxRadius) || hitboxRadius <= 0
                || !Float.isFinite(respawnSeconds) || respawnSeconds < 0) throw new IllegalArgumentException("Invalid mob profile.");
        }
    }
    private static final class State {
        final Entity entity;
        final Profile profile;
        final float spawnX, spawnY, spawnZ, spawnYaw, maxHealth;
        float attackRemaining;
        float respawnRemaining = -1;
        State(Entity entity, Profile profile) {
            this.entity = entity; this.profile = profile;
            spawnX = entity.x(); spawnY = entity.y(); spawnZ = entity.z(); spawnYaw = entity.yaw();
            maxHealth = entity.maxHealth();
        }
    }

    private final Map<String, State> states = new LinkedHashMap<>();

    void register(Entity entity, float aggroRange, float attackRange, float moveSpeed,
                  float attackDamage, float attackCooldown, float hitboxRadius, float respawnSeconds) {
        Objects.requireNonNull(entity, "entity");
        if (entity.type() != EntityType.MOB) throw new IllegalArgumentException("Only MOB entities can be registered as hostile.");
        Profile profile = new Profile(aggroRange, attackRange, moveSpeed, attackDamage, attackCooldown, hitboxRadius, respawnSeconds);
        if (states.putIfAbsent(entity.id(), new State(entity, profile)) != null) throw new IllegalArgumentException("Mob already registered: " + entity.id());
    }

    List<String> update(float deltaSeconds, PlayerController player, PlayerStats playerStats,
                        EntityWorld entities, StaticCollisionWorld collisionWorld) {
        float dt = Math.max(0, Math.min(deltaSeconds, 0.05f));
        List<String> events = new ArrayList<>();
        for (State state : states.values()) {
            Entity mob = entities.get(state.entity.id());
            if (mob == null) {
                if (state.respawnRemaining >= 0 && state.profile.respawnSeconds() > 0) {
                    state.respawnRemaining -= dt;
                    if (state.respawnRemaining <= 0) {
                        state.entity.setPosition(state.spawnX, state.spawnY, state.spawnZ).setYaw(state.spawnYaw)
                            .setHealth(state.maxHealth, state.maxHealth).setVisible(true);
                        entities.add(state.entity);
                        state.respawnRemaining = -1;
                        state.attackRemaining = 0;
                        events.add(state.entity.displayName() + " has respawned.");
                    }
                }
                continue;
            }
            if (!playerStats.alive()) continue;
            double dx = player.x() - mob.x(), dz = player.z() - mob.z();
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            double verticalDistance = Math.abs(player.feetY() - mob.y());
            if (horizontalDistance > state.profile.aggroRange() || verticalDistance > 4.0) {
                state.attackRemaining = Math.max(0, state.attackRemaining - dt);
                continue;
            }
            if (horizontalDistance > state.profile.attackRange()) {
                double move = Math.min(state.profile.moveSpeed() * dt, horizontalDistance - state.profile.attackRange() * 0.8);
                if (horizontalDistance > 1.0e-5) {
                    double mx = dx / horizontalDistance * move, mz = dz / horizontalDistance * move;
                    double nx = mob.x() + mx, nz = mob.z() + mz;
                    AxisAlignedBox nextX = new AxisAlignedBox(nx - state.profile.hitboxRadius(), mob.y(), mob.z() - state.profile.hitboxRadius(),
                        nx + state.profile.hitboxRadius(), mob.y() + mob.model().height(), mob.z() + state.profile.hitboxRadius());
                    if (!collisionWorld.intersects(nextX)) mob.setPosition((float) nx, mob.y(), mob.z());
                    AxisAlignedBox nextZ = new AxisAlignedBox(mob.x() - state.profile.hitboxRadius(), mob.y(), nz - state.profile.hitboxRadius(),
                        mob.x() + state.profile.hitboxRadius(), mob.y() + mob.model().height(), nz + state.profile.hitboxRadius());
                    if (!collisionWorld.intersects(nextZ)) mob.setPosition(mob.x(), mob.y(), (float) nz);
                    mob.setYaw((float) Math.atan2(dx, dz));
                }
                state.attackRemaining = Math.max(0, state.attackRemaining - dt);
            } else {
                state.attackRemaining -= dt;
                if (state.attackRemaining <= 0) {
                    float applied = playerStats.takeDamage(state.profile.attackDamage());
                    events.add(mob.displayName() + " hit you for " + Math.round(applied) + " damage. ");
                    state.attackRemaining = state.profile.attackCooldown();
                }
            }
        }
        return events;
    }

    String playerAttack(Entity target, EntityWorld world, PlayerStats attacker) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(attacker, "attacker");
        if (target.type() != EntityType.MOB || world.get(target.id()) == null || !attacker.alive()) return "";
        float damage = attacker.attackDamage();
        float applied = target.takeDamage(damage);
        String message = "Hit " + target.displayName() + " for " + Math.round(applied) + " damage.";
        if (target.health() <= 0) {
            world.remove(target.id());
            State state = states.get(target.id());
            if (state != null && state.profile.respawnSeconds() > 0) state.respawnRemaining = state.profile.respawnSeconds();
            message = "Defeated " + target.displayName() + "!";
        }
        return message;
    }
}
