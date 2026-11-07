package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.render.api.MeshData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Creates a centered unit cuboid and maps each Java model face into its own packed atlas region.
 * UV values are resolved before placement scale is applied. UVs outside 0..1 tile within their
 * texture's atlas region (never across neighbouring atlas textures), matching repeat behavior.
 */
final class TexturedCuboidMeshFactory {
    private static final int MAX_UV_SEGMENTS_PER_AXIS = 128;
    private static final int MAX_QUADS_PER_FACE = 4096;

    record FaceTexture(String textureId, int rotationDegrees, boolean mirrorU, boolean mirrorV,
                       float u0, float v0, float u1, float v1, boolean customUv) {
        FaceTexture(String textureId, int rotationDegrees, boolean mirrorU, boolean mirrorV) {
            this(textureId, rotationDegrees, mirrorU, mirrorV, 0.0f, 0.0f, 1.0f, 1.0f, false);
        }
        static FaceTexture uv(String textureId, int rotationDegrees, float u0, float v0, float u1, float v1) {
            return new FaceTexture(textureId, rotationDegrees, false, false, u0, v0, u1, v1, true);
        }
        static FaceTexture skipped() {
            return new FaceTexture("__skip__", 0, false, false, 0, 0, 1, 1, false);
        }
    }

    private record P(float x, float y, float z) { }
    private record Uv(float u, float v) { }
    private record Face(String name, P[] points, Uv[] uv, float shade) { }

    private static final Face[] FACES = {
        new Face("TOP", new P[]{p(-.5f,.5f,-.5f),p(-.5f,.5f,.5f),p(.5f,.5f,.5f),p(.5f,.5f,-.5f)},
            new Uv[]{uv(0,0),uv(0,1),uv(1,1),uv(1,0)}, 1.00f),
        new Face("FRONT", new P[]{p(-.5f,-.5f,.5f),p(.5f,-.5f,.5f),p(.5f,.5f,.5f),p(-.5f,.5f,.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .80f),
        new Face("RIGHT", new P[]{p(.5f,-.5f,-.5f),p(.5f,.5f,-.5f),p(.5f,.5f,.5f),p(.5f,-.5f,.5f)},
            new Uv[]{uv(1,1),uv(1,0),uv(0,0),uv(0,1)}, .68f),
        new Face("BACK", new P[]{p(.5f,-.5f,-.5f),p(-.5f,-.5f,-.5f),p(-.5f,.5f,-.5f),p(.5f,.5f,-.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .56f),
        new Face("LEFT", new P[]{p(-.5f,-.5f,-.5f),p(-.5f,-.5f,.5f),p(-.5f,.5f,.5f),p(-.5f,.5f,-.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .65f),
        new Face("BOTTOM", new P[]{p(-.5f,-.5f,-.5f),p(.5f,-.5f,-.5f),p(.5f,-.5f,.5f),p(-.5f,-.5f,.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .42f)
    };

    private TexturedCuboidMeshFactory() { }

    static MeshData create(String defaultTexture, Map<String, FaceTexture> mappings,
                           Map<String, VModelTextureAtlas.Region> regions) {
        MeshData.Builder builder = MeshData.builder();
        for (Face face : FACES) {
            FaceTexture map = mappings.getOrDefault(face.name(), new FaceTexture(defaultTexture, 0, false, false));
            if ("__skip__".equals(map.textureId())) continue;
            VModelTextureAtlas.Region region = regions.get(map.textureId());
            if (region == null) throw new IllegalArgumentException("Unknown texture '" + map.textureId() + "' mapped to face " + face.name());

            Uv[] rawUv = new Uv[4];
            for (int i = 0; i < 4; i++) {
                if (map.customUv()) {
                    // Minecraft Java BlockFaceUV rotates the UV-corner assignment with
                    // getVertexRotatedRev(i) = (i + 4 - rotation/90) % 4. Rotating
                    // normalized coordinates themselves swaps/stretch axes on non-square
                    // UV rectangles and disagrees with Blockbench/vanilla exports.
                    int turns = Math.floorMod(map.rotationDegrees(), 360) / 90;
                    Uv base = face.uv()[Math.floorMod(i - turns, 4)];
                    rawUv[i] = uv(map.u0() + base.u() * (map.u1() - map.u0()),
                                  map.v0() + base.v() * (map.v1() - map.v0()));
                } else {
                    // Preserve the legacy .vmodel normalized-UV rotation behavior.
                    rawUv[i] = transform(face.uv()[i], map.rotationDegrees(), map.mirrorU(), map.mirrorV());
                }
            }
            List<Float> sBreaks = parameterBreaks(rawUv[0], rawUv[1]);
            List<Float> tBreaks = parameterBreaks(rawUv[0], rawUv[3]);
            long quads = (long) (sBreaks.size() - 1) * (tBreaks.size() - 1);
            if (quads > MAX_QUADS_PER_FACE) {
                throw new IllegalArgumentException("UV rectangle for face " + face.name()
                    + " needs " + quads + " atlas tiles; maximum is " + MAX_QUADS_PER_FACE + ".");
            }

            for (int si = 0; si + 1 < sBreaks.size(); si++) {
                float s0 = sBreaks.get(si), s1 = sBreaks.get(si + 1);
                for (int ti = 0; ti + 1 < tBreaks.size(); ti++) {
                    float t0 = tBreaks.get(ti), t1 = tBreaks.get(ti + 1);
                    Uv[] cellUv = {
                        interpolate(rawUv[0], rawUv[1], rawUv[3], s0, t0),
                        interpolate(rawUv[0], rawUv[1], rawUv[3], s1, t0),
                        interpolate(rawUv[0], rawUv[1], rawUv[3], s1, t1),
                        interpolate(rawUv[0], rawUv[1], rawUv[3], s0, t1)
                    };
                    float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY;
                    float minV = Float.POSITIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
                    for (Uv value : cellUv) {
                        minU = Math.min(minU, value.u()); maxU = Math.max(maxU, value.u());
                        minV = Math.min(minV, value.v()); maxV = Math.max(maxV, value.v());
                    }
                    // Choose the source-texture repeat containing this cell. Using the cell
                    // midpoint keeps a coordinate exactly on a seam as 1.0 on one side and 0.0
                    // on the other, with no UV bleeding between atlas slots.
                    float tileU = (float) Math.floor((minU + maxU) * 0.5f);
                    float tileV = (float) Math.floor((minV + maxV) * 0.5f);

                    P[] cellPoints = {
                        interpolate(face.points()[0], face.points()[1], face.points()[3], s0, t0),
                        interpolate(face.points()[0], face.points()[1], face.points()[3], s1, t0),
                        interpolate(face.points()[0], face.points()[1], face.points()[3], s1, t1),
                        interpolate(face.points()[0], face.points()[1], face.points()[3], s0, t1)
                    };
                    int first = builder.vertexCount();
                    for (int i = 0; i < 4; i++) {
                        Uv sourceUv = cellUv[i];
                        float localU = clampUnit(sourceUv.u() - tileU);
                        float localV = clampUnit(sourceUv.v() - tileV);
                        float atlasU = region.u0() + localU * (region.u1() - region.u0());
                        float atlasV = region.v0() + localV * (region.v1() - region.v0());
                        P point = cellPoints[i];
                        float lowerOcclusion = point.y() < 0.0f ? 0.075f : 0.0f;
                        float cornerOcclusion = (point.x() < 0.0f ? 0.025f : 0.0f)
                            + (point.z() < 0.0f ? 0.030f : 0.0f);
                        float cornerCrease = (point.y() < 0.0f && point.x() * point.z() > 0.0f) ? 0.025f : 0.0f;
                        float light = Math.max(0.28f, face.shade() * (1.0f - lowerOcclusion - cornerOcclusion - cornerCrease));
                        builder.vertex(point.x(), point.y(), point.z(), atlasU, atlasV,
                            Math.min(1.0f, light * (point.y() > 0.0f ? 1.035f : 1.015f)),
                            Math.min(1.0f, light * 1.005f),
                            Math.min(1.0f, light * (point.y() < 0.0f ? 1.015f : 0.975f)));
                    }
                    builder.quad(first, first + 1, first + 2, first + 3);
                }
            }
        }
        return builder.build();
    }

    /** Breaks one face parameter wherever either UV axis crosses a texture-repeat boundary. */
    private static List<Float> parameterBreaks(Uv start, Uv end) {
        ArrayList<Float> values = new ArrayList<>();
        values.add(0.0f); values.add(1.0f);
        addIntegerCrossings(start.u(), end.u(), values);
        addIntegerCrossings(start.v(), end.v(), values);
        values.sort(Float::compare);
        ArrayList<Float> unique = new ArrayList<>(values.size());
        for (float value : values) {
            float clamped = Math.max(0.0f, Math.min(1.0f, value));
            if (unique.isEmpty() || Math.abs(clamped - unique.get(unique.size() - 1)) > 1.0e-5f) unique.add(clamped);
        }
        if (unique.size() - 1 > MAX_UV_SEGMENTS_PER_AXIS) {
            throw new IllegalArgumentException("Model face UV span exceeds " + MAX_UV_SEGMENTS_PER_AXIS + " texture repeats on one axis.");
        }
        return unique;
    }

    private static void addIntegerCrossings(float start, float end, List<Float> breaks) {
        float delta = end - start;
        if (Math.abs(delta) < 1.0e-7f) return;
        float min = Math.min(start, end), max = Math.max(start, end);
        int first = (int) Math.floor(min) + 1;
        int last = (int) Math.ceil(max) - 1;
        if ((long) last - first > MAX_UV_SEGMENTS_PER_AXIS + 1L) {
            throw new IllegalArgumentException("Model face UV span exceeds " + MAX_UV_SEGMENTS_PER_AXIS + " texture repeats on one axis.");
        }
        for (int boundary = first; boundary <= last; boundary++) {
            float t = (boundary - start) / delta;
            if (t > 1.0e-5f && t < 1.0f - 1.0e-5f) breaks.add(t);
        }
    }

    private static Uv interpolate(Uv p0, Uv p1, Uv p3, float s, float t) {
        return uv(p0.u() + s * (p1.u() - p0.u()) + t * (p3.u() - p0.u()),
                  p0.v() + s * (p1.v() - p0.v()) + t * (p3.v() - p0.v()));
    }

    private static P interpolate(P p0, P p1, P p3, float s, float t) {
        return p(p0.x() + s * (p1.x() - p0.x()) + t * (p3.x() - p0.x()),
                 p0.y() + s * (p1.y() - p0.y()) + t * (p3.y() - p0.y()),
                 p0.z() + s * (p1.z() - p0.z()) + t * (p3.z() - p0.z()));
    }

    private static float clampUnit(float value) {
        if (value < 0.0f && value > -1.0e-4f) return 0.0f;
        if (value > 1.0f && value < 1.0001f) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static Uv transform(Uv source, int degrees, boolean mirrorU, boolean mirrorV) {
        float u = mirrorU ? 1.0f - source.u() : source.u();
        float v = mirrorV ? 1.0f - source.v() : source.v();
        return switch (Math.floorMod(degrees, 360)) {
            case 0 -> uv(u, v);
            case 90 -> uv(v, 1.0f - u);
            case 180 -> uv(1.0f - u, 1.0f - v);
            case 270 -> uv(1.0f - v, u);
            default -> throw new IllegalArgumentException("Texture rotation must be 0, 90, 180 or 270 degrees.");
        };
    }

    private static P p(float x,float y,float z) { return new P(x,y,z); }
    private static Uv uv(float u,float v) { return new Uv(u,v); }
}
