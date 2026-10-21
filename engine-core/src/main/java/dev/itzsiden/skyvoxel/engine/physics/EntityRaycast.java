package dev.itzsiden.skyvoxel.engine.physics;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;

import java.util.Objects;
import java.util.Optional;

/** Entity-model-aware ray targeting. Static world geometry occludes entities behind it. */
public final class EntityRaycast {
    public record Hit(Entity entity, double distance, AxisAlignedBox hitbox) {
        public Hit {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(hitbox, "hitbox");
            if (!Double.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid hit distance.");
        }
    }

    private EntityRaycast() { }

    public static AxisAlignedBox hitbox(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        double yaw = entity.yaw(), c = Math.cos(yaw), s = Math.sin(yaw);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (EntityModel.Part part : entity.model().parts()) {
            double partYaw = part.rotationY();
            double pC = Math.cos(partYaw), pS = Math.sin(partYaw);
            double hxLocal = Math.abs(pC) * part.width() * 0.5 + Math.abs(pS) * part.depth() * 0.5;
            double hzLocal = Math.abs(pS) * part.width() * 0.5 + Math.abs(pC) * part.depth() * 0.5;
            double hx = Math.abs(c) * hxLocal + Math.abs(s) * hzLocal;
            double hz = Math.abs(s) * hxLocal + Math.abs(c) * hzLocal;
            double cx = entity.x() + part.offsetX() * c + part.offsetZ() * s;
            double cz = entity.z() - part.offsetX() * s + part.offsetZ() * c;
            double cy = entity.y() + part.offsetY();
            minX = Math.min(minX, cx - hx); maxX = Math.max(maxX, cx + hx);
            minY = Math.min(minY, cy - part.height() * 0.5); maxY = Math.max(maxY, cy + part.height() * 0.5);
            minZ = Math.min(minZ, cz - hz); maxZ = Math.max(maxZ, cz + hz);
        }
        // Small forgiving margin helps the low-poly models feel targetable without huge invisible hitboxes.
        return new AxisAlignedBox(minX - 0.035, minY, minZ - 0.035, maxX + 0.035, maxY, maxZ + 0.035);
    }

    public static Optional<Hit> raycast(EntityWorld entities, StaticCollisionWorld world,
                                        String ignoredEntityId,
                                        double ox, double oy, double oz,
                                        double dx, double dy, double dz, double maxDistance) {
        Objects.requireNonNull(entities, "entities");
        Objects.requireNonNull(world, "world");
        if (!finite(ox, oy, oz, dx, dy, dz, maxDistance) || maxDistance < 0) {
            throw new IllegalArgumentException("Ray values must be finite and reach must be non-negative.");
        }
        double directionLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (directionLength < 1.0e-12) return Optional.empty();
        dx /= directionLength; dy /= directionLength; dz /= directionLength;
        double limit = maxDistance;
        Optional<RaycastHit> wallHit = world.raycast(ox, oy, oz, dx, dy, dz, maxDistance);
        if (wallHit.isPresent()) limit = Math.min(limit, wallHit.get().distance() - 0.015);
        if (limit < 0) return Optional.empty();

        Entity nearest = null;
        AxisAlignedBox nearestBox = null;
        double nearestDistance = limit + 1.0;
        for (Entity entity : entities.entities()) {
            if (entity.id().equals(ignoredEntityId) || !entity.visible() || !entity.modelVisible() || entity.health() <= 0) continue;
            AxisAlignedBox box = hitbox(entity);
            double distance = intersect(ox, oy, oz, dx, dy, dz, box, limit);
            if (distance >= 0 && distance < nearestDistance) {
                nearest = entity; nearestBox = box; nearestDistance = distance;
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(new Hit(nearest, nearestDistance, nearestBox));
    }

    private static double intersect(double ox, double oy, double oz, double dx, double dy, double dz,
                                    AxisAlignedBox box, double maxDistance) {
        double near = 0, far = maxDistance;
        double[] origins = {ox, oy, oz}, directions = {dx, dy, dz};
        double[] mins = {box.minX(), box.minY(), box.minZ()}, maxs = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < 1.0e-12) {
                if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) return -1;
                continue;
            }
            double a = (mins[axis] - origins[axis]) / directions[axis];
            double b = (maxs[axis] - origins[axis]) / directions[axis];
            if (a > b) { double t = a; a = b; b = t; }
            near = Math.max(near, a); far = Math.min(far, b);
            if (near > far) return -1;
        }
        return far < 0 || near > maxDistance ? -1 : near;
    }

    private static boolean finite(double... values) {
        for (double v : values) if (!Double.isFinite(v)) return false;
        return true;
    }
}
