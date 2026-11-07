package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.render.api.MeshData;

import java.util.Map;

/** Makes a unit cuboid whose six faces independently address regions in a model-local texture atlas. */
final class TexturedCuboidMeshFactory {
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
        // Minecraft Java Block/Item UVs are direction-relative, not one generic
        // left-to-right mapping for all vertical faces. EAST reverses Z and NORTH
        // reverses X so labels/patterns keep the same orientation as Blockbench.
        new Face("RIGHT", new P[]{p(.5f,-.5f,-.5f),p(.5f,.5f,-.5f),p(.5f,.5f,.5f),p(.5f,-.5f,.5f)},
            new Uv[]{uv(1,1),uv(1,0),uv(0,0),uv(0,1)}, .68f),
        new Face("BACK", new P[]{p(.5f,-.5f,-.5f),p(-.5f,-.5f,-.5f),p(-.5f,.5f,-.5f),p(.5f,.5f,-.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .56f),
        new Face("LEFT", new P[]{p(-.5f,-.5f,-.5f),p(-.5f,-.5f,.5f),p(-.5f,.5f,.5f),p(-.5f,.5f,-.5f)},
            new Uv[]{uv(0,1),uv(1,1),uv(1,0),uv(0,0)}, .65f),
        // DOWN maps V in the opposite Z direction (v = 16 - z in the
        // Java model coordinate convention); the old mapping mirrored this face.
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
            int first = builder.vertexCount();
            for (int i = 0; i < 4; i++) {
                Uv base = transform(face.uv()[i], map.rotationDegrees(), map.mirrorU(), map.mirrorV());
                float localU = map.customUv() ? map.u0() + base.u() * (map.u1() - map.u0()) : base.u();
                float localV = map.customUv() ? map.v0() + base.v() * (map.v1() - map.v0()) : base.v();
                float u = region.u0() + localU * (region.u1() - region.u0());
                float v = region.v0() + localV * (region.v1() - region.v0());
                P p = face.points()[i];
                float lowerOcclusion = p.y() < 0.0f ? 0.075f : 0.0f;
                float cornerOcclusion = (p.x() < 0.0f ? 0.025f : 0.0f)
                    + (p.z() < 0.0f ? 0.030f : 0.0f);
                float cornerCrease = (p.y() < 0.0f && p.x() * p.z() > 0.0f) ? 0.025f : 0.0f;
                float light = Math.max(0.28f, face.shade() * (1.0f - lowerOcclusion - cornerOcclusion - cornerCrease));
                builder.vertex(p.x(), p.y(), p.z(), u, v,
                    Math.min(1.0f, light * (p.y() > 0.0f ? 1.035f : 1.015f)),
                    Math.min(1.0f, light * 1.005f),
                    Math.min(1.0f, light * (p.y() < 0.0f ? 1.015f : 0.975f)));
            }
            builder.quad(first, first + 1, first + 2, first + 3);
        }
        return builder.build();
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
