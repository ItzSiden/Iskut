package dev.itzsiden.skyvoxel.engine.scene;

/**
 * Backend-independent position, Euler rotation (radians), and scale.
 * The returned model matrix is column-major and owned by this Transform; callers must not mutate it.
 */
public final class Transform {
    private float x, y, z;
    private float rotationX, rotationY, rotationZ;
    private float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
    private final float[] matrix = new float[16];
    private boolean dirty = true;

    public Transform setPosition(float x, float y, float z) {
        this.x = finite(x, "x"); this.y = finite(y, "y"); this.z = finite(z, "z");
        dirty = true; return this;
    }
    public Transform translate(float dx, float dy, float dz) {
        return setPosition(x + dx, y + dy, z + dz);
    }
    public Transform setRotation(float xRadians, float yRadians, float zRadians) {
        rotationX = finite(xRadians, "rotationX");
        rotationY = finite(yRadians, "rotationY");
        rotationZ = finite(zRadians, "rotationZ");
        dirty = true; return this;
    }
    public Transform setScale(float uniform) { return setScale(uniform, uniform, uniform); }
    public Transform setScale(float x, float y, float z) {
        this.scaleX = positiveScale(x, "scaleX");
        this.scaleY = positiveScale(y, "scaleY");
        this.scaleZ = positiveScale(z, "scaleZ");
        dirty = true; return this;
    }

    /**
     * Sets an explicit column-major model matrix. Intended for camera-relative viewmodels and
     * other composed transforms that cannot be represented by one Euler rotation without drift.
     * The matrix is copied. Later calls to the regular transform setters rebuild from the
     * extracted translation/scale and zero Euler rotation.
     */
    public Transform setModelMatrix(float[] explicitMatrix) {
        if (explicitMatrix == null || explicitMatrix.length != 16)
            throw new IllegalArgumentException("Model matrix must contain 16 floats.");
        for (float value : explicitMatrix) if (!Float.isFinite(value))
            throw new IllegalArgumentException("Model matrix values must be finite.");
        System.arraycopy(explicitMatrix, 0, matrix, 0, 16);
        x = matrix[12]; y = matrix[13]; z = matrix[14];
        scaleX = positiveScale(length(matrix[0], matrix[1], matrix[2]), "matrix scaleX");
        scaleY = positiveScale(length(matrix[4], matrix[5], matrix[6]), "matrix scaleY");
        scaleZ = positiveScale(length(matrix[8], matrix[9], matrix[10]), "matrix scaleZ");
        rotationX = rotationY = rotationZ = 0.0f;
        dirty = false;
        return this;
    }

    public float x() { return x; }
    public float y() { return y; }
    public float z() { return z; }
    public float rotationX() { return rotationX; }
    public float rotationY() { return rotationY; }
    public float rotationZ() { return rotationZ; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }

    public float[] modelMatrix() {
        if (dirty) rebuildMatrix();
        return matrix;
    }

    private void rebuildMatrix() {
        // Quaternion for Rz * Ry * Rx, built without a math-library dependency.
        double hx = rotationX * 0.5, hy = rotationY * 0.5, hz = rotationZ * 0.5;
        double sx = Math.sin(hx), cx = Math.cos(hx);
        double sy = Math.sin(hy), cy = Math.cos(hy);
        double sz = Math.sin(hz), cz = Math.cos(hz);
        double qx = sx * cy * cz - cx * sy * sz;
        double qy = cx * sy * cz + sx * cy * sz;
        double qz = cx * cy * sz - sx * sy * cz;
        double qw = cx * cy * cz + sx * sy * sz;
        double xx = qx * qx, yy = qy * qy, zz = qz * qz;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz;
        double wx = qw * qx, wy = qw * qy, wz = qw * qz;

        // Column-major T * R * S. Scale each rotation basis column independently.
        matrix[0] = (float) (1.0 - 2.0 * (yy + zz)) * scaleX;
        matrix[1] = (float) (2.0 * (xy + wz)) * scaleX;
        matrix[2] = (float) (2.0 * (xz - wy)) * scaleX;
        matrix[3] = 0.0f;
        matrix[4] = (float) (2.0 * (xy - wz)) * scaleY;
        matrix[5] = (float) (1.0 - 2.0 * (xx + zz)) * scaleY;
        matrix[6] = (float) (2.0 * (yz + wx)) * scaleY;
        matrix[7] = 0.0f;
        matrix[8] = (float) (2.0 * (xz + wy)) * scaleZ;
        matrix[9] = (float) (2.0 * (yz - wx)) * scaleZ;
        matrix[10] = (float) (1.0 - 2.0 * (xx + yy)) * scaleZ;
        matrix[11] = 0.0f;
        matrix[12] = x; matrix[13] = y; matrix[14] = z; matrix[15] = 1.0f;
        dirty = false;
    }

    private static float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " must be finite.");
        return value;
    }
    private static float positiveScale(float value, String name) {
        finite(value, name);
        if (value <= 0.0f) throw new IllegalArgumentException(name + " must be > 0.");
        return value;
    }
}
