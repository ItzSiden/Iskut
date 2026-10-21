package dev.itzsiden.skyvoxel.engine.camera;

/** Backend-independent perspective camera. Matrices are column-major OpenGL-style clip matrices. */
public final class Camera {
    private float x, y = 1.65f, z = 8.0f;
    private float yawRadians, pitchRadians;
    private float fieldOfViewRadians = (float) Math.toRadians(72.0);
    private float nearPlane = 0.05f, farPlane = 250.0f;
    private final float[] view = new float[16];
    private final float[] projection = new float[16];
    private final float[] viewProjection = new float[16];

    public Camera setPosition(float x, float y, float z) {
        this.x = finite(x); this.y = finite(y); this.z = finite(z); return this;
    }
    public Camera setRotation(float yawRadians, float pitchRadians) {
        this.yawRadians = finite(yawRadians); this.pitchRadians = finite(pitchRadians); return this;
    }
    public Camera setPerspective(float fovRadians, float nearPlane, float farPlane) {
        if (!Float.isFinite(fovRadians) || fovRadians <= 0.01f || fovRadians >= Math.PI - 0.01f) {
            throw new IllegalArgumentException("FOV must be within (0, PI).");
        }
        if (!Float.isFinite(nearPlane) || !Float.isFinite(farPlane) || nearPlane <= 0 || farPlane <= nearPlane) {
            throw new IllegalArgumentException("Perspective planes must satisfy 0 < near < far.");
        }
        this.fieldOfViewRadians = fovRadians; this.nearPlane = nearPlane; this.farPlane = farPlane; return this;
    }

    /** Returns a reusable array owned by this camera; do not mutate it. */
    public float[] viewProjection(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Viewport must be positive.");
        float aspect = (float) width / (float) height;
        float f = (float) (1.0 / Math.tan(fieldOfViewRadians * 0.5));
        zero(projection);
        projection[0] = f / aspect;
        projection[5] = f;
        projection[10] = (farPlane + nearPlane) / (nearPlane - farPlane);
        projection[11] = -1.0f;
        projection[14] = (2.0f * farPlane * nearPlane) / (nearPlane - farPlane);

        float cosPitch = (float) Math.cos(pitchRadians);
        float fx = (float) Math.sin(yawRadians) * cosPitch;
        float fy = (float) Math.sin(pitchRadians);
        float fz = -(float) Math.cos(yawRadians) * cosPitch;
        float fLength = length(fx, fy, fz);
        fx /= fLength; fy /= fLength; fz /= fLength;
        // side = forward x worldUp. For a near-vertical look, pitch is capped by the controller.
        float sx = -fz;
        float sy = 0.0f;
        float sz = fx;
        float sLength = length(sx, sy, sz);
        sx /= sLength; sy /= sLength; sz /= sLength;
        float ux = sy * fz - sz * fy;
        float uy = sz * fx - sx * fz;
        float uz = sx * fy - sy * fx;

        zero(view);
        view[0] = sx; view[1] = ux; view[2] = -fx; view[3] = 0.0f;
        view[4] = sy; view[5] = uy; view[6] = -fy; view[7] = 0.0f;
        view[8] = sz; view[9] = uz; view[10] = -fz; view[11] = 0.0f;
        view[12] = -(sx * x + sy * y + sz * z);
        view[13] = -(ux * x + uy * y + uz * z);
        view[14] = fx * x + fy * y + fz * z;
        view[15] = 1.0f;
        multiply(projection, view, viewProjection);
        return viewProjection;
    }

    private static void multiply(float[] a, float[] b, float[] out) {
        // Column-major out = a * b. The arrays do not alias here.
        for (int col = 0; col < 4; col++) {
            int bc = col * 4;
            for (int row = 0; row < 4; row++) {
                out[bc + row] = a[row] * b[bc]
                    + a[4 + row] * b[bc + 1]
                    + a[8 + row] * b[bc + 2]
                    + a[12 + row] * b[bc + 3];
            }
        }
    }
    private static void zero(float[] values) { java.util.Arrays.fill(values, 0.0f); }
    private static float length(float x, float y, float z) { return (float) Math.sqrt(x*x + y*y + z*z); }
    private static float finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Camera values must be finite.");
        return value;
    }
}
