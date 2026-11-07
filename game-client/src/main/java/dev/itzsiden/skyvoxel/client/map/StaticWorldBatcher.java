package dev.itzsiden.skyvoxel.client.map;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.render.api.MeshData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a rendering-only scene from a handcrafted map. Static objects sharing material and a
 * spatial chunk are transformed into one mesh, cutting CPU submission/transform overhead sharply.
 * The original scene remains untouched and is still used for collision, interactions and targeting.
 * Objects carrying gameplay interactions are intentionally left individual so they can be removed
 * or highlighted without rebuilding a whole chunk.
 */
final class StaticWorldBatcher {
    static final float CHUNK_SIZE = 48.0f;
    private static final float AO_RADIUS = 2.25f;
    private static final float AO_STRENGTH = 0.34f;
    private static final float SHADOW_REACH = 15.0f;
    private static final float COPLANAR_OFFSET = 0.001f;
    private static final float COPLANAR_EPSILON = 0.0001f;
    // Keep baked sunlight direction aligned with the Iskut daylight skybox.
    private static final float SUN_LENGTH = (float) Math.sqrt(0.34f * 0.34f + 0.56f * 0.56f + 1.0f);
    private static final float SUN_X = 0.34f / SUN_LENGTH;
    private static final float SUN_Y = 0.56f / SUN_LENGTH;
    private static final float SUN_Z = -1.0f / SUN_LENGTH;

    private record Key(Material material, int chunkX, int chunkZ) { }
    private record WorldBounds(RenderObject owner, float minX, float minY, float minZ,
                               float maxX, float maxY, float maxZ, int order) { }
    private record FacePlane(int axis, float coordinate, int sign,
                             float minU, float minV, float maxU, float maxV) { }

    private static final class Group {
        final Material material;
        final int chunkX, chunkZ;
        final MeshData.Builder builder = MeshData.builder();
        int objects;
        Group(Key key) { material = key.material(); chunkX = key.chunkX(); chunkZ = key.chunkZ(); }
    }

    private StaticWorldBatcher() { }

    static Scene build(Scene source, Set<String> interactiveObjectIds) {
        Scene output = new Scene();
        Map<Key, Group> groups = new LinkedHashMap<>();
        List<WorldBounds> allBounds = new ArrayList<>(source.size());
        Map<RenderObject, WorldBounds> boundsByObject = new java.util.IdentityHashMap<>();
        int objectOrder = 0;
        for (RenderObject object : source.objects()) {
            // Full-world water/sky surfaces must not enter contact AO or coplanar offset queries.
            if (object.staticBatchExcluded()) continue;
            WorldBounds bounds = worldBounds(object, objectOrder++);
            allBounds.add(bounds);
            boundsByObject.put(object, bounds);
        }
        for (RenderObject object : source.objects()) {
            if (object.staticBatchExcluded() || interactiveObjectIds.contains(object.id())) {
                output.add(object); // preserve their original mesh, stable ID, and transform
                continue;
            }
            WorldBounds ownerBounds = boundsByObject.get(object);
            float[] m = object.transform().modelMatrix();
            int chunkX = floorChunk(m[12]);
            int chunkZ = floorChunk(m[14]);
            Key key = new Key(object.material(), chunkX, chunkZ);
            Group group = groups.computeIfAbsent(key, Group::new);
            appendWorldMesh(group.builder, object, ownerBounds, allBounds);
            group.objects++;
        }

        int batchIndex = 0;
        for (Group group : groups.values()) {
            if (group.builder.isEmpty()) continue;
            String materialId = safe(group.material.name());
            String id = "__static_" + materialId + "_" + group.chunkX + "_" + group.chunkZ + "_" + batchIndex++;
            Mesh mesh = new Mesh(id + "_mesh", group.builder.build());
            output.add(new RenderObject(id, mesh, group.material));
        }
        return output;
    }

    private static int floorChunk(float coordinate) { return (int) Math.floor(coordinate / CHUNK_SIZE); }

    private static void appendWorldMesh(MeshData.Builder out, RenderObject object, WorldBounds ownerBounds,
                                        List<WorldBounds> allBounds) {
        MeshData data = object.mesh().data();
        float[] vertices = data.vertices();
        int[] indices = data.indices();
        float[] m = object.transform().modelMatrix();
        List<WorldBounds> coplanarCandidates = coplanarCandidates(ownerBounds, allBounds);
        float[] offsets = new float[vertices.length / MeshData.FLOATS_PER_VERTEX * 3];
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i], b = indices[i + 1], c = indices[i + 2];
            FacePlane face = facePlane(vertices, a, b, c, m);
            if (face == null) continue;
            float offset = coplanarOffset(face, ownerBounds.order(), coplanarCandidates);
            if (offset == 0.0f) continue;
            setFaceOffset(offsets, a, face.axis(), face.sign() * offset);
            setFaceOffset(offsets, b, face.axis(), face.sign() * offset);
            setFaceOffset(offsets, c, face.axis(), face.sign() * offset);
        }
        int base = out.vertexCount();
        float localTop = object.mesh().maxY() - 0.001f;
        for (int i = 0; i < vertices.length; i += MeshData.FLOATS_PER_VERTEX) {
            int vertex = i / MeshData.FLOATS_PER_VERTEX;
            float x = vertices[i], y = vertices[i + 1], z = vertices[i + 2];
            float wx = m[0] * x + m[4] * y + m[8] * z + m[12];
            float wy = m[1] * x + m[5] * y + m[9] * z + m[13];
            float wz = m[2] * x + m[6] * y + m[10] * z + m[14];
            wx += offsets[vertex * 3];
            wy += offsets[vertex * 3 + 1];
            wz += offsets[vertex * 3 + 2];
            float bakedLight = 1.0f;
            if (y >= localTop) {
                float ao = contactOcclusion(wx, wy, wz, object, allBounds);
                float shadow = sunlightVisibility(wx, wy, wz, object, allBounds);
                bakedLight = ao * shadow;
            }
            out.vertex(wx, wy, wz, vertices[i + 3], vertices[i + 4],
                vertices[i + 5] * bakedLight, vertices[i + 6] * bakedLight, vertices[i + 7] * bakedLight);
        }
        for (int i = 0; i + 2 < indices.length; i += 3) {
            out.triangle(base + indices[i], base + indices[i + 1], base + indices[i + 2]);
        }
    }

    private static List<WorldBounds> coplanarCandidates(WorldBounds owner, List<WorldBounds> bounds) {
        List<WorldBounds> candidates = new ArrayList<>();
        for (WorldBounds other : bounds) {
            if (other.owner() == owner.owner()) continue;
            if (overlaps(other.minX(), other.maxX(), owner.minX(), owner.maxX())
                && overlaps(other.minZ(), other.maxZ(), owner.minZ(), owner.maxZ())
                && (samePlane(other.minY(), owner.maxY()) || samePlane(other.maxY(), owner.minY())
                    || samePlane(other.minY(), owner.minY()) || samePlane(other.maxY(), owner.maxY()))) {
                candidates.add(other);
                continue;
            }
            if (overlaps(other.minY(), other.maxY(), owner.minY(), owner.maxY())
                && overlaps(other.minZ(), other.maxZ(), owner.minZ(), owner.maxZ())
                && (samePlane(other.minX(), owner.maxX()) || samePlane(other.maxX(), owner.minX())
                    || samePlane(other.minX(), owner.minX()) || samePlane(other.maxX(), owner.maxX()))) {
                candidates.add(other);
                continue;
            }
            if (overlaps(other.minX(), other.maxX(), owner.minX(), owner.maxX())
                && overlaps(other.minY(), other.maxY(), owner.minY(), owner.maxY())
                && (samePlane(other.minZ(), owner.maxZ()) || samePlane(other.maxZ(), owner.minZ())
                    || samePlane(other.minZ(), owner.minZ()) || samePlane(other.maxZ(), owner.maxZ()))) {
                candidates.add(other);
            }
        }
        return candidates;
    }

    private static FacePlane facePlane(float[] vertices, int ia, int ib, int ic, float[] model) {
        float ax = worldX(vertices, ia, model), ay = worldY(vertices, ia, model), az = worldZ(vertices, ia, model);
        float bx = worldX(vertices, ib, model), by = worldY(vertices, ib, model), bz = worldZ(vertices, ib, model);
        float cx = worldX(vertices, ic, model), cy = worldY(vertices, ic, model), cz = worldZ(vertices, ic, model);
        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-8f) return null;
        nx /= length; ny /= length; nz /= length;

        int axis = Math.abs(nx) > 0.999f ? 0 : Math.abs(ny) > 0.999f ? 1 : Math.abs(nz) > 0.999f ? 2 : -1;
        if (axis < 0) return null;
        float sign = axis == 0 ? nx : axis == 1 ? ny : nz;
        float plane = axis == 0 ? (ax + bx + cx) / 3.0f
            : axis == 1 ? (ay + by + cy) / 3.0f : (az + bz + cz) / 3.0f;
        float au = axis == 0 ? ay : ax, bu = axis == 0 ? by : bx, cu = axis == 0 ? cy : cx;
        float av = axis == 2 ? ay : az, bv = axis == 2 ? by : bz, cv = axis == 2 ? cy : cz;
        return new FacePlane(axis, plane, sign > 0.0f ? 1 : -1,
            min(au, bu, cu), min(av, bv, cv), max(au, bu, cu), max(av, bv, cv));
    }

    private static float worldX(float[] vertices, int index, float[] model) {
        int offset = index * MeshData.FLOATS_PER_VERTEX;
        float x = vertices[offset], y = vertices[offset + 1], z = vertices[offset + 2];
        return model[0] * x + model[4] * y + model[8] * z + model[12];
    }

    private static float worldY(float[] vertices, int index, float[] model) {
        int offset = index * MeshData.FLOATS_PER_VERTEX;
        float x = vertices[offset], y = vertices[offset + 1], z = vertices[offset + 2];
        return model[1] * x + model[5] * y + model[9] * z + model[13];
    }

    private static float worldZ(float[] vertices, int index, float[] model) {
        int offset = index * MeshData.FLOATS_PER_VERTEX;
        float x = vertices[offset], y = vertices[offset + 1], z = vertices[offset + 2];
        return model[2] * x + model[6] * y + model[10] * z + model[14];
    }

    private static float coplanarOffset(FacePlane face, int ownerOrder, List<WorldBounds> candidates) {
        int earlierSameFacingFaces = 0;
        boolean overlapsCoplanarFace = false;
        for (WorldBounds other : candidates) {
            float otherPlane = face.sign() > 0 ? max(other, face.axis()) : min(other, face.axis());
            if (!samePlane(face.coordinate(), otherPlane) || !overlapsFace(face, other)) continue;
            overlapsCoplanarFace = true;
            if (other.order() < ownerOrder && sameFacingFace(face, other)) earlierSameFacingFaces++;
        }
        if (!overlapsCoplanarFace) return 0.0f;
        int layer = earlierSameFacingFaces + 1;
        return layer * COPLANAR_OFFSET;
    }

    private static boolean sameFacingFace(FacePlane face, WorldBounds bounds) {
        return samePlane(face.coordinate(), face.sign() > 0 ? max(bounds, face.axis()) : min(bounds, face.axis()));
    }

    private static boolean overlapsFace(FacePlane face, WorldBounds bounds) {
        return switch (face.axis()) {
            case 0 -> overlaps(face.minU(), face.maxU(), bounds.minY(), bounds.maxY())
                && overlaps(face.minV(), face.maxV(), bounds.minZ(), bounds.maxZ());
            case 1 -> overlaps(face.minU(), face.maxU(), bounds.minX(), bounds.maxX())
                && overlaps(face.minV(), face.maxV(), bounds.minZ(), bounds.maxZ());
            default -> overlaps(face.minU(), face.maxU(), bounds.minX(), bounds.maxX())
                && overlaps(face.minV(), face.maxV(), bounds.minY(), bounds.maxY());
        };
    }

    private static float min(WorldBounds bounds, int axis) {
        return axis == 0 ? bounds.minX() : axis == 1 ? bounds.minY() : bounds.minZ();
    }

    private static float max(WorldBounds bounds, int axis) {
        return axis == 0 ? bounds.maxX() : axis == 1 ? bounds.maxY() : bounds.maxZ();
    }

    private static float min(float a, float b, float c) { return Math.min(a, Math.min(b, c)); }
    private static float max(float a, float b, float c) { return Math.max(a, Math.max(b, c)); }

    private static boolean samePlane(float a, float b) { return Math.abs(a - b) <= COPLANAR_EPSILON; }
    private static boolean overlaps(float aMin, float aMax, float bMin, float bMax) {
        return aMax > bMin + COPLANAR_EPSILON && aMin < bMax - COPLANAR_EPSILON;
    }

    private static void setFaceOffset(float[] offsets, int vertex, int axis, float amount) {
        offsets[vertex * 3 + axis] = amount;
    }

    /** Bakes a small, smooth contact-darkening halo into static top edges around nearby structures. */
    private static float contactOcclusion(float x, float y, float z, RenderObject owner, List<WorldBounds> bounds) {
        float darkest = 1.0f;
        for (WorldBounds other : bounds) {
            if (other.owner() == owner || other.maxY() < y + 0.55f) continue;
            // The neighbour must start on roughly the same floor/ledge plane as this vertex.
            if (Math.abs(other.minY() - y) > 0.32f) continue;
            float dx = x < other.minX() ? other.minX() - x : (x > other.maxX() ? x - other.maxX() : 0.0f);
            float dz = z < other.minZ() ? other.minZ() - z : (z > other.maxZ() ? z - other.maxZ() : 0.0f);
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            if (distance >= AO_RADIUS) continue;
            float t = 1.0f - distance / AO_RADIUS;
            // Smoothstep fade: no hard ring and only one strongest nearby occluder is needed.
            t = t * t * (3.0f - 2.0f * t);
            darkest = Math.min(darkest, 1.0f - AO_STRENGTH * t);
            if (darkest <= 1.0f - AO_STRENGTH + 0.001f) break;
        }
        return darkest;
    }

    /** Bakes short directional sun shadows into subdivided static surfaces at map-load time. */
    private static float sunlightVisibility(float x, float y, float z, RenderObject owner, List<WorldBounds> bounds) {
        // Start a fraction above the receiving surface so the surface itself never self-intersects.
        float ox = x + SUN_X * 0.04f;
        float oy = y + 0.04f;
        float oz = z + SUN_Z * 0.04f;
        for (WorldBounds other : bounds) {
            if (other.owner() == owner || other.maxY() <= y + 0.65f) continue;
            if (other.minY() > y + SHADOW_REACH * SUN_Y + 0.5f) continue;
            // Cheap conservative XY/Z bound around the short sun ray before doing slab intersection.
            if (other.maxX() < ox - 0.15f || other.minX() > ox + SUN_X * SHADOW_REACH + 0.15f) continue;
            if (other.minZ() > oz + 0.15f || other.maxZ() < oz + SUN_Z * SHADOW_REACH - 0.15f) continue;
            if (rayHitsBounds(ox, oy, oz, other)) return 0.80f;
        }
        return 1.0f;
    }

    private static boolean rayHitsBounds(float ox, float oy, float oz, WorldBounds box) {
        float tMin = 0.0f, tMax = SHADOW_REACH;
        float[] origins = {ox, oy, oz};
        float[] dirs = {SUN_X, SUN_Y, SUN_Z};
        float[] mins = {box.minX(), box.minY(), box.minZ()};
        float[] maxs = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            float d = dirs[axis], o = origins[axis];
            if (Math.abs(d) < 1.0e-6f) {
                if (o < mins[axis] || o > maxs[axis]) return false;
                continue;
            }
            float a = (mins[axis] - o) / d;
            float b = (maxs[axis] - o) / d;
            if (a > b) { float swap = a; a = b; b = swap; }
            tMin = Math.max(tMin, a);
            tMax = Math.min(tMax, b);
            if (tMax < tMin) return false;
        }
        return tMax >= 0.05f && tMin < SHADOW_REACH;
    }

    private static WorldBounds worldBounds(RenderObject object, int order) {
        float[] m = object.transform().modelMatrix();
        float cx = object.mesh().centerX(), cy = object.mesh().centerY(), cz = object.mesh().centerZ();
        float hx = (object.mesh().maxX() - object.mesh().minX()) * 0.5f;
        float hy = (object.mesh().maxY() - object.mesh().minY()) * 0.5f;
        float hz = (object.mesh().maxZ() - object.mesh().minZ()) * 0.5f;
        float wx = m[0] * cx + m[4] * cy + m[8] * cz + m[12];
        float wy = m[1] * cx + m[5] * cy + m[9] * cz + m[13];
        float wz = m[2] * cx + m[6] * cy + m[10] * cz + m[14];
        float ex = Math.abs(m[0]) * hx + Math.abs(m[4]) * hy + Math.abs(m[8]) * hz;
        float ey = Math.abs(m[1]) * hx + Math.abs(m[5]) * hy + Math.abs(m[9]) * hz;
        float ez = Math.abs(m[2]) * hx + Math.abs(m[6]) * hy + Math.abs(m[10]) * hz;
        return new WorldBounds(object, wx - ex, wy - ey, wz - ez, wx + ex, wy + ey, wz + ez, order);
    }

    private static String safe(String name) { return name.replaceAll("[^A-Za-z0-9_-]", "_"); }
}
