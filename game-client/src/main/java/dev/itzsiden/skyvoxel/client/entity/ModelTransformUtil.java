package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

/** Shared matrix helpers for Blockbench element rotations and structure/model placement transforms. */
public final class ModelTransformUtil {
    private ModelTransformUtil() { }

    /** Backwards-compatible uniform-scale/Y-rotation overload used by item drops. */
    public static void setPlacedPart(Transform transform, float x, float y, float z,
                                     float placementYaw, EntityModel.Part part, float uniformScale) {
        setPlacedPart(transform, x, y, z, rotation(0.0f, placementYaw, 0.0f), part,
            uniformScale, uniformScale, uniformScale);
    }

    /**
     * Applies model-space non-uniform scale, then the model/structure rotation, and finally the
     * imported element rotation. The element mesh is a centered unit cuboid, so its dimensions are
     * applied to the three basis columns of the final matrix.
     */
    public static void setPlacedPart(Transform transform, float x, float y, float z,
                                     float[] placedRotation, EntityModel.Part part,
                                     float scaleX, float scaleY, float scaleZ) {
        if (placedRotation == null || placedRotation.length != 9)
            throw new IllegalArgumentException("Placed rotation must contain 9 values.");
        float[] partRotation = rotation(part.rotationX(), part.rotationY(), part.rotationZ());
        float[] modelScale = scaling(scaleX, scaleY, scaleZ);
        float[] combined = multiply(multiply(placedRotation, modelScale), partRotation);
        float[] matrix = new float[16];
        matrix[0] = combined[0] * part.width(); matrix[1] = combined[3] * part.width(); matrix[2] = combined[6] * part.width();
        matrix[4] = combined[1] * part.height(); matrix[5] = combined[4] * part.height(); matrix[6] = combined[7] * part.height();
        matrix[8] = combined[2] * part.depth(); matrix[9] = combined[5] * part.depth(); matrix[10] = combined[8] * part.depth();
        matrix[12] = x; matrix[13] = y; matrix[14] = z; matrix[15] = 1.0f;
        transform.setModelMatrix(matrix);
    }

    /** Returns structure yaw combined with local model Euler rotation (RstructureYaw * Rmodel). */
    public static float[] placedRotation(float structureYaw, float rotationX, float rotationY, float rotationZ) {
        return multiply(rotation(0.0f, structureYaw, 0.0f), rotation(rotationX, rotationY, rotationZ));
    }

    /** Transforms a model-local point by per-axis scale and then its placed rotation. */
    public static float[] transformOffset(float x, float y, float z, float[] placedRotation,
                                          float scaleX, float scaleY, float scaleZ) {
        if (placedRotation == null || placedRotation.length != 9)
            throw new IllegalArgumentException("Placed rotation must contain 9 values.");
        float sx = x * scaleX, sy = y * scaleY, sz = z * scaleZ;
        return new float[]{
            placedRotation[0] * sx + placedRotation[1] * sy + placedRotation[2] * sz,
            placedRotation[3] * sx + placedRotation[4] * sy + placedRotation[5] * sz,
            placedRotation[6] * sx + placedRotation[7] * sy + placedRotation[8] * sz
        };
    }

    private static float[] scaling(float x, float y, float z) {
        return new float[]{x,0,0, 0,y,0, 0,0,z};
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
