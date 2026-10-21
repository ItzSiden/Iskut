package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.render.api.MeshData;

/** Builds textured cuboids with repeating world-unit UVs and low-cost smooth-light vertex gradients. */
final class WorldCuboidMeshFactory {
    private record P(float x, float y, float z, float u, float v) { }
    private record Face(P a, P b, P c, P d, float shade) { }
    /** Top surfaces get denser vertices for soft baked shadows; hidden bottoms/sides stay cheaper. */
    private static final float TOP_LIGHTING_GRID = 2.5f;
    private static final float OTHER_FACE_GRID = 4.0f;
    private static final int MAX_FACE_SEGMENTS = 64;

    private WorldCuboidMeshFactory() { }

    static MeshData create(float width, float height, float depth) {
        return create(width, height, depth, 1.0f);
    }

    static MeshData create(float width, float height, float depth, float tileWorldSize) {
        if (!Float.isFinite(tileWorldSize) || tileWorldSize <= 0.0f) {
            throw new IllegalArgumentException("Texture tile size must be positive and finite.");
        }
        float u = width / tileWorldSize, v = depth / tileWorldSize, h = height / tileWorldSize;
        float x0=-.5f, x1=.5f, y0=-.5f, y1=.5f, z0=-.5f, z1=.5f;
        Face[] faces = {
            new Face(p(x0,y1,z0,0,0), p(x0,y1,z1,0,v), p(x1,y1,z1,u,v), p(x1,y1,z0,u,0), 1.00f),
            new Face(p(x0,y0,z1,0,h), p(x1,y0,z1,u,h), p(x1,y1,z1,u,0), p(x0,y1,z1,0,0), .80f),
            new Face(p(x1,y0,z0,0,h), p(x1,y1,z0,0,0), p(x1,y1,z1,v,0), p(x1,y0,z1,v,h), .68f),
            new Face(p(x1,y0,z0,0,h), p(x0,y0,z0,u,h), p(x0,y1,z0,u,0), p(x1,y1,z0,0,0), .56f),
            new Face(p(x0,y0,z0,0,h), p(x0,y0,z1,v,h), p(x0,y1,z1,v,0), p(x0,y1,z0,0,0), .65f),
            new Face(p(x0,y0,z0,0,0), p(x1,y0,z0,u,0), p(x1,y0,z1,u,v), p(x0,y0,z1,0,v), .42f)
        };
        MeshData.Builder builder = MeshData.builder();
        boolean hugeFlatPlane = width >= 256.0f && depth >= 256.0f && height <= 1.0f;
        for (Face face : faces) {
            boolean horizontal = Math.abs(face.a().y() - face.b().y()) < 1.0e-6f
                && Math.abs(face.a().y() - face.d().y()) < 1.0e-6f;
            // Ocean/background planes are visually flat and non-collidable. Avoid creating tens of
            // thousands of hidden vertex-light cells on their top AND bottom surfaces.
            float grid = hugeFlatPlane && horizontal ? 256.0f
                : horizontal && face.a().y() > 0.0f ? TOP_LIGHTING_GRID : OTHER_FACE_GRID;
            int segmentsU = segments(face.a(), face.b(), width, height, depth, grid);
            int segmentsV = segments(face.a(), face.d(), width, height, depth, grid);
            for (int j = 0; j < segmentsV; j++) {
                float v0 = j / (float) segmentsV, v1 = (j + 1) / (float) segmentsV;
                for (int i = 0; i < segmentsU; i++) {
                    float u0 = i / (float) segmentsU, u1 = (i + 1) / (float) segmentsU;
                    P a = interpolate(face.a(), face.b(), face.d(), u0, v0);
                    P b = interpolate(face.a(), face.b(), face.d(), u1, v0);
                    P c = interpolate(face.a(), face.b(), face.d(), u1, v1);
                    P d = interpolate(face.a(), face.b(), face.d(), u0, v1);
                    int first = builder.vertexCount();
                    add(builder, a, face.shade()); add(builder, b, face.shade());
                    add(builder, c, face.shade()); add(builder, d, face.shade());
                    builder.quad(first, first + 1, first + 2, first + 3);
                }
            }
        }
        return builder.build();
    }

    private static int segments(P a, P b, float width, float height, float depth, float grid) {
        float dx = (b.x() - a.x()) * width;
        float dy = (b.y() - a.y()) * height;
        float dz = (b.z() - a.z()) * depth;
        float worldLength = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(1, Math.min(MAX_FACE_SEGMENTS, (int) Math.ceil(worldLength / grid)));
    }

    private static P interpolate(P a, P b, P d, float u, float v) {
        return new P(
            a.x() + (b.x() - a.x()) * u + (d.x() - a.x()) * v,
            a.y() + (b.y() - a.y()) * u + (d.y() - a.y()) * v,
            a.z() + (b.z() - a.z()) * u + (d.z() - a.z()) * v,
            a.u() + (b.u() - a.u()) * u + (d.u() - a.u()) * v,
            a.v() + (b.v() - a.v()) * u + (d.v() - a.v()) * v);
    }

    /**
     * Cheap baked lighting: directional face shading, mild warm sun tint, and vertex gradients.
     * StaticWorldBatcher adds proximity-aware contact AO to these colors at map-load time.
     */
    private static void add(MeshData.Builder builder, P p, float faceLight) {
        float lowerOcclusion = p.y() < 0.0f ? 0.075f : 0.0f;
        float cornerOcclusion = (p.x() < 0.0f ? 0.025f : 0.0f)
            + (p.z() < 0.0f ? 0.030f : 0.0f);
        float cornerCrease = (p.y() < 0.0f && p.x() * p.z() > 0.0f) ? 0.025f : 0.0f;
        float light = Math.max(0.28f, faceLight * (1.0f - lowerOcclusion - cornerOcclusion - cornerCrease));
        float red = Math.min(1.0f, light * (p.y() > 0.0f ? 1.035f : 1.015f));
        float green = Math.min(1.0f, light * 1.005f);
        float blue = Math.min(1.0f, light * (p.y() < 0.0f ? 1.015f : 0.975f));
        builder.vertex(p.x(), p.y(), p.z(), p.u(), p.v(), red, green, blue);
    }

    private static P p(float x,float y,float z,float u,float v) { return new P(x,y,z,u,v); }
}
