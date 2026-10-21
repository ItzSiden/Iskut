package dev.itzsiden.skyvoxel.engine.physics;

import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot of the collidable static objects in a scene. Call rebuild after the map's collision
 * geometry changes. Object AABBs conservatively account for scale and rotation.
 */
public final class StaticCollisionWorld {
    public record Collider(RenderObject object, AxisAlignedBox bounds) {
        public Collider {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    private final List<Collider> colliders = new ArrayList<>();
    private final List<Collider> collidersView = Collections.unmodifiableList(colliders);

    public StaticCollisionWorld(Scene scene) {
        rebuild(scene);
    }

    public void rebuild(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        colliders.clear();
        for (RenderObject object : scene.objects()) {
            if (!object.collidable()) continue;
            colliders.add(new Collider(object, worldBounds(object.mesh(), object.transform().modelMatrix())));
        }
    }

    public List<Collider> colliders() {
        return collidersView;
    }

    public boolean intersects(AxisAlignedBox box) {
        for (Collider collider : colliders) {
            if (box.intersects(collider.bounds())) return true;
        }
        return false;
    }

    public Optional<RaycastHit> raycast(double ox, double oy, double oz,
                                        double dx, double dy, double dz, double maxDistance) {
        if (!finite(ox, oy, oz, dx, dy, dz, maxDistance) || maxDistance < 0.0) {
            throw new IllegalArgumentException("Ray parameters must be finite and maxDistance non-negative.");
        }
        double directionLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (directionLength < 1.0e-12) return Optional.empty();
        dx /= directionLength; dy /= directionLength; dz /= directionLength;

        RenderObject nearest = null;
        double nearestDistance = maxDistance + 1.0;
        for (Collider collider : colliders) {
            double distance = intersectRayAabb(ox, oy, oz, dx, dy, dz, collider.bounds(), maxDistance);
            if (distance >= 0.0 && distance < nearestDistance) {
                nearestDistance = distance;
                nearest = collider.object();
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(new RaycastHit(nearest, nearestDistance));
    }

    private static AxisAlignedBox worldBounds(Mesh mesh, float[] m) {
        double cx = (mesh.minX() + mesh.maxX()) * 0.5;
        double cy = (mesh.minY() + mesh.maxY()) * 0.5;
        double cz = (mesh.minZ() + mesh.maxZ()) * 0.5;
        double hx = (mesh.maxX() - mesh.minX()) * 0.5;
        double hy = (mesh.maxY() - mesh.minY()) * 0.5;
        double hz = (mesh.maxZ() - mesh.minZ()) * 0.5;
        double wx = m[0] * cx + m[4] * cy + m[8] * cz + m[12];
        double wy = m[1] * cx + m[5] * cy + m[9] * cz + m[13];
        double wz = m[2] * cx + m[6] * cy + m[10] * cz + m[14];
        double ex = Math.abs(m[0]) * hx + Math.abs(m[4]) * hy + Math.abs(m[8]) * hz;
        double ey = Math.abs(m[1]) * hx + Math.abs(m[5]) * hy + Math.abs(m[9]) * hz;
        double ez = Math.abs(m[2]) * hx + Math.abs(m[6]) * hy + Math.abs(m[10]) * hz;
        return new AxisAlignedBox(wx - ex, wy - ey, wz - ez, wx + ex, wy + ey, wz + ez);
    }

    private static double intersectRayAabb(double ox, double oy, double oz,
                                           double dx, double dy, double dz,
                                           AxisAlignedBox box, double maxDistance) {
        double near = 0.0;
        double far = maxDistance;
        double[] origins = {ox, oy, oz};
        double[] directions = {dx, dy, dz};
        double[] mins = {box.minX(), box.minY(), box.minZ()};
        double[] maxs = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            double direction = directions[axis];
            if (Math.abs(direction) < 1.0e-12) {
                if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) return -1.0;
                continue;
            }
            double t1 = (mins[axis] - origins[axis]) / direction;
            double t2 = (maxs[axis] - origins[axis]) / direction;
            if (t1 > t2) { double swap = t1; t1 = t2; t2 = swap; }
            near = Math.max(near, t1);
            far = Math.min(far, t2);
            if (near > far) return -1.0;
        }
        if (far < 0.0 || near > maxDistance) return -1.0;
        return near;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
