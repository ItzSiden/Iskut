package dev.itzsiden.skyvoxel.client.items;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.render.api.MeshData;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Generates transparent isometric inventory thumbnails directly from authored cuboid .vmodel parts. */
public final class ItemIconGenerator {
    public static final int ICON_SIZE = 64;
    public static final int GENERATOR_VERSION = 3;
    private static final Map<TextureData, BufferedImage> ATLAS_IMAGES = new WeakHashMap<>();

    private static final float CAMERA_X = 0.55f;
    private static final float CAMERA_Y = 0.62f;
    private static final float CAMERA_Z = 0.56f;
    private static final float CAMERA_LENGTH = length(CAMERA_X, CAMERA_Y, CAMERA_Z);

    private ItemIconGenerator() { }

    public static BufferedImage render(EntityModel model) {
        List<Face> faces = new ArrayList<>();
        List<Point3> allPoints = new ArrayList<>();
        for (EntityModel.Part part : model.parts()) appendPart(part, faces, allPoints);
        if (allPoints.isEmpty()) throw new IllegalArgumentException("Cannot render an empty item model.");

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (Point3 p : allPoints) {
            float px = projectX(p.x, p.z), py = projectY(p.x, p.y, p.z);
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }
        float modelW = Math.max(0.001f, maxX - minX), modelH = Math.max(0.001f, maxY - minY);
        float scale = Math.min((ICON_SIZE - 15.0f) / modelW, (ICON_SIZE - 15.0f) / modelH);
        float offsetX = (ICON_SIZE - modelW * scale) * 0.5f - minX * scale;
        float offsetY = (ICON_SIZE - modelH * scale) * 0.5f - minY * scale + 0.5f;

        BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // A tiny neutral contact shadow grounds the icon without adding a background tile.
            g.setColor(new Color(0x2B000000, true));
            g.fill(new Ellipse2D.Float(ICON_SIZE * 0.25f, ICON_SIZE * 0.79f, ICON_SIZE * 0.50f, ICON_SIZE * 0.095f));
            faces.sort(Comparator.comparingDouble(Face::depth)); // far surfaces first, camera-facing surfaces last
            for (Face face : faces) {
                int[] xs = new int[4], ys = new int[4];
                for (int i = 0; i < 4; i++) {
                    Point3 p = face.points.get(i);
                    xs[i] = Math.round(projectX(p.x, p.z) * scale + offsetX);
                    ys[i] = Math.round(projectY(p.x, p.y, p.z) * scale + offsetY);
                }
                if (face.material.texture() != null && drawTexturedFace(g, face, xs, ys)) {
                    // Texture and vertex-baked face shade were applied by the texture painter.
                } else {
                    g.setColor(shade(face.material, face.shade));
                    g.fillPolygon(xs, ys, 4);
                }
                g.setStroke(new BasicStroke(0.65f));
                g.setColor(new Color(0x56000000, true));
                g.drawPolygon(xs, ys, 4);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    /** Paints a face by affine-mapping the exact atlas tile onto its isometric projected quad. */
    private static boolean drawTexturedFace(Graphics2D g, Face face, int[] xs, int[] ys) {
        TextureData texture = face.material.texture();
        if (texture == null) return false;
        float[] uv = face.uvs;
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
        float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            minU = Math.min(minU, uv[i * 2]); maxU = Math.max(maxU, uv[i * 2]);
            minV = Math.min(minV, uv[i * 2 + 1]); maxV = Math.max(maxV, uv[i * 2 + 1]);
        }
        // One-pixel fallback tiles intentionally have a zero UV span; render them as solid colour.
        if (maxU - minU < 1.0e-8f || maxV - minV < 1.0e-8f) {
            BufferedImage atlas = atlasImage(texture);
            int sx = Math.max(0, Math.min(texture.width() - 1, (int) Math.floor(minU * texture.width())));
            int sy = Math.max(0, Math.min(texture.height() - 1, (int) Math.floor(minV * texture.height())));
            int argb = atlas.getRGB(sx, sy);
            if ((argb >>> 24) == 0) return true;
            int r = Math.min(255, Math.round(((argb >>> 16) & 255) * face.shade * face.material.red()));
            int gg = Math.min(255, Math.round(((argb >>> 8) & 255) * face.shade * face.material.green()));
            int b = Math.min(255, Math.round((argb & 255) * face.shade * face.material.blue()));
            g.setColor(new Color(r, gg, b, (argb >>> 24) & 255));
            g.fillPolygon(xs, ys, 4);
            return true;
        }
        BufferedImage atlas = atlasImage(texture);
        int x0 = Math.max(0, Math.min(texture.width() - 1, (int) Math.floor(minU * texture.width())));
        int y0 = Math.max(0, Math.min(texture.height() - 1, (int) Math.floor(minV * texture.height())));
        int x1 = Math.max(x0 + 1, Math.min(texture.width(), (int) Math.ceil(maxU * texture.width())));
        int y1 = Math.max(y0 + 1, Math.min(texture.height(), (int) Math.ceil(maxV * texture.height())));
        int cropW = x1 - x0, cropH = y1 - y0;
        BufferedImage crop = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cropH; y++) for (int x = 0; x < cropW; x++) {
            int c = atlas.getRGB(x0 + x, y0 + y);
            int a = (c >>> 24) & 255;
            int r = Math.min(255, Math.round(((c >>> 16) & 255) * face.shade * face.material.red()));
            int gg = Math.min(255, Math.round(((c >>> 8) & 255) * face.shade * face.material.green()));
            int b = Math.min(255, Math.round((c & 255) * face.shade * face.material.blue()));
            crop.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
        }
        double[] sx = new double[4], sy = new double[4];
        for (int i = 0; i < 4; i++) {
            sx[i] = (uv[i * 2] - minU) / (maxU - minU) * cropW;
            sy[i] = (uv[i * 2 + 1] - minV) / (maxV - minV) * cropH;
        }
        // The affine transform maps texture-space vertices 0, 1 and 3 onto the matching quad vertices.
        double dx1 = sx[1] - sx[0], dy1 = sy[1] - sy[0];
        double dx2 = sx[3] - sx[0], dy2 = sy[3] - sy[0];
        double det = dx1 * dy2 - dx2 * dy1;
        if (Math.abs(det) < 1.0e-8) return false;
        double tx1 = xs[1] - xs[0], ty1 = ys[1] - ys[0];
        double tx2 = xs[3] - xs[0], ty2 = ys[3] - ys[0];
        double m00 = (tx1 * dy2 - tx2 * dy1) / det;
        double m01 = (dx1 * tx2 - dx2 * tx1) / det;
        double m10 = (ty1 * dy2 - ty2 * dy1) / det;
        double m11 = (dx1 * ty2 - dx2 * ty1) / det;
        double translateX = xs[0] - m00 * sx[0] - m01 * sy[0];
        double translateY = ys[0] - m10 * sx[0] - m11 * sy[0];
        AffineTransform transform = new AffineTransform(m00, m10, m01, m11, translateX, translateY);
        Object oldInterpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(crop, transform, null);
        if (oldInterpolation != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
        return true;
    }

    private static BufferedImage atlasImage(TextureData texture) {
        synchronized (ATLAS_IMAGES) {
            return ATLAS_IMAGES.computeIfAbsent(texture, key -> {
                BufferedImage image = new BufferedImage(key.width(), key.height(), BufferedImage.TYPE_INT_ARGB);
                int[] pixels = key.pixels();
                image.setRGB(0, 0, key.width(), key.height(), pixels, 0, key.width());
                return image;
            });
        }
    }

    private static void appendPart(EntityModel.Part p, List<Face> faces, List<Point3> all) {
        float hx = p.width() * 0.5f, hy = p.height() * 0.5f, hz = p.depth() * 0.5f;
        Point3[][] localFaces = {
            {pt(-hx, hy, -hz), pt(-hx, hy, hz), pt(hx, hy, hz), pt(hx, hy, -hz)}, // top
            {pt(-hx, -hy, hz), pt(hx, -hy, hz), pt(hx, hy, hz), pt(-hx, hy, hz)}, // front
            {pt(hx, -hy, -hz), pt(hx, hy, -hz), pt(hx, hy, hz), pt(hx, -hy, hz)}, // right
            {pt(hx, -hy, -hz), pt(-hx, -hy, -hz), pt(-hx, hy, -hz), pt(hx, hy, -hz)}, // back
            {pt(-hx, -hy, -hz), pt(-hx, -hy, hz), pt(-hx, hy, hz), pt(-hx, hy, -hz)}, // left
            {pt(-hx, -hy, -hz), pt(hx, -hy, -hz), pt(hx, -hy, hz), pt(-hx, -hy, hz)} // bottom
        };
        Point3[] normals = {pt(0,1,0), pt(0,0,1), pt(1,0,0), pt(0,0,-1), pt(-1,0,0), pt(0,-1,0)};
        float[] shades = {1.00f, 0.82f, 0.68f, 0.58f, 0.74f, 0.44f};
        float cos = (float) Math.cos(p.rotationY()), sin = (float) Math.sin(p.rotationY());
        float[] meshVertices = p.mesh().data().vertices();
        for (int f = 0; f < localFaces.length; f++) {
            Point3 rotatedNormal = rotate(normals[f], cos, sin);
            float facing = dot(rotatedNormal, CAMERA_X / CAMERA_LENGTH, CAMERA_Y / CAMERA_LENGTH, CAMERA_Z / CAMERA_LENGTH);
            if (facing <= 0.015f) continue;
            List<Point3> pts = new ArrayList<>(4);
            float dx = 0, dy = 0, dz = 0;
            for (Point3 v : localFaces[f]) {
                Point3 r = rotate(v, cos, sin);
                Point3 world = pt(r.x + p.offsetX(), r.y + p.offsetY(), r.z + p.offsetZ());
                pts.add(world); all.add(world);
                dx += world.x; dy += world.y; dz += world.z;
            }
            float depth = (dx * CAMERA_X + dy * CAMERA_Y + dz * CAMERA_Z) / (4.0f * CAMERA_LENGTH);
            float[] uvs = new float[8];
            int vertexBase = f * 4;
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = (vertexBase + vertex) * MeshData.FLOATS_PER_VERTEX;
                if (offset + MeshData.OFFSET_UV + 1 < meshVertices.length) {
                    uvs[vertex * 2] = meshVertices[offset + MeshData.OFFSET_UV];
                    uvs[vertex * 2 + 1] = meshVertices[offset + MeshData.OFFSET_UV + 1];
                }
            }
            faces.add(new Face(List.copyOf(pts), p.material(), shades[f], depth, uvs));
        }
        // Keep every corner in the framing bounds, including corners on culled faces.
        for (float x : new float[]{-hx, hx}) for (float y : new float[]{-hy, hy}) for (float z : new float[]{-hz, hz}) {
            Point3 r = rotate(pt(x, y, z), cos, sin);
            all.add(pt(r.x + p.offsetX(), r.y + p.offsetY(), r.z + p.offsetZ()));
        }
    }

    private static Point3 rotate(Point3 p, float cos, float sin) {
        return pt(p.x * cos + p.z * sin, p.y, -p.x * sin + p.z * cos);
    }
    private static float projectX(float x, float z) { return (x - z) * 0.70710678f; }
    private static float projectY(float x, float y, float z) { return (x + z) * 0.40824829f - y * 0.81649658f; }
    private static Color shade(Material material, float amount) {
        int r = Math.min(255, Math.round(material.red() * amount * 255));
        int g = Math.min(255, Math.round(material.green() * amount * 255));
        int b = Math.min(255, Math.round(material.blue() * amount * 255));
        return new Color(r, g, b, 255);
    }
    private static float dot(Point3 p, float x, float y, float z) { return p.x * x + p.y * y + p.z * z; }
    private static float length(float x, float y, float z) { return (float) Math.sqrt(x*x + y*y + z*z); }
    private static Point3 pt(float x, float y, float z) { return new Point3(x, y, z); }
    private record Point3(float x, float y, float z) { }
    private record Face(List<Point3> points, Material material, float shade, float depth, float[] uvs) { }
}
