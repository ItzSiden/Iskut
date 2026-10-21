package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

/** Shared matrix composition for Blockbench element rotations plus a placement yaw. */
public final class ModelTransformUtil {
    private ModelTransformUtil() { }

    public static void setPlacedPart(Transform transform, float x, float y, float z,
                                     float placementYaw, EntityModel.Part part, float uniformScale) {
        float[] partRotation = rotation(part.rotationX(), part.rotationY(), part.rotationZ());
        float[] yawRotation = rotation(0.0f, placementYaw, 0.0f);
        float[] combined = multiply(yawRotation, partRotation);
        float sx = part.width() * uniformScale;
        float sy = part.height() * uniformScale;
        float sz = part.depth() * uniformScale;
        float[] matrix = new float[16];
        matrix[0] = combined[0] * sx; matrix[1] = combined[3] * sx; matrix[2] = combined[6] * sx;
        matrix[4] = combined[1] * sy; matrix[5] = combined[4] * sy; matrix[6] = combined[7] * sy;
        matrix[8] = combined[2] * sz; matrix[9] = combined[5] * sz; matrix[10] = combined[8] * sz;
        matrix[12] = x; matrix[13] = y; matrix[14] = z; matrix[15] = 1.0f;
        transform.setModelMatrix(matrix);
    }

    /** Returns a row-major 3x3 matrix using the engine's Rz * Ry * Rx convention. */
    public static float[] rotation(float rx, float ry, float rz) {
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        float cy = (float) Math.cos(ry), sy = (float) Math.sin(ry);
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
        float[] rxM = {1,0,0, 0,cx,-sx, 0,sx,cx};
        float[] ryM = {cy,0,sy, 0,1,0, -sy,0,cy};
        float[] rzM = {cz,-sz,0, sz,cz,0, 0,0,1};
        return multiply(multiply(rzM, ryM), rxM);
    }

    /** Multiplies two row-major 3x3 matrices. */
    public static float[] multiply(float[] a, float[] b) {
        float[] out = new float[9];
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            out[row * 3 + col] = a[row * 3] * b[col]
                + a[row * 3 + 1] * b[3 + col]
                + a[row * 3 + 2] * b[6 + col];
        }
        return out;
    }
}
