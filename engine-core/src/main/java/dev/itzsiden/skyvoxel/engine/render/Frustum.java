package dev.itzsiden.skyvoxel.engine.render;

/** Six normalized world-space planes extracted from a column-major view-projection matrix. */
final class Frustum {
    private final float[][] planes = new float[6][4];

    void update(float[] m) {
        if (m == null || m.length != 16) throw new IllegalArgumentException("View-projection must have 16 floats.");
        // clip inequalities are -w <= x,y,z <= w (OpenGL NDC). Matrix is column-major.
        setPlane(0, m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12]); // left
        setPlane(1, m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12]); // right
        setPlane(2, m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13]); // bottom
        setPlane(3, m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13]); // top
        setPlane(4, m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14]); // near
        setPlane(5, m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14]); // far
    }

    boolean containsSphere(float x, float y, float z, float radius) {
        for (float[] p : planes) {
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < -radius) return false;
        }
        return true;
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        float invLength = 1.0f / (float) Math.sqrt(a*a + b*b + c*c);
        float[] plane = planes[index];
        plane[0] = a * invLength; plane[1] = b * invLength;
        plane[2] = c * invLength; plane[3] = d * invLength;
    }
}
