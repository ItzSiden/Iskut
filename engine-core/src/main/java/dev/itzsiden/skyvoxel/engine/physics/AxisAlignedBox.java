package dev.itzsiden.skyvoxel.engine.physics;

/** Immutable world-space axis-aligned bounding box. Touching faces do not count as overlap. */
public record AxisAlignedBox(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
    public AxisAlignedBox {
        if (!finite(minX, minY, minZ, maxX, maxY, maxZ)) {
            throw new IllegalArgumentException("AABB coordinates must be finite.");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("AABB minimum must not exceed maximum.");
        }
    }

    public boolean intersects(AxisAlignedBox other) {
        return maxX > other.minX && minX < other.maxX
            && maxY > other.minY && minY < other.maxY
            && maxZ > other.minZ && minZ < other.maxZ;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
