package dev.itzsiden.skyvoxel.engine.scene;

import java.util.Objects;

/** One placed mesh instance. Multiple objects may share the exact same Mesh resource. */
public final class RenderObject {
    private final String id;
    private final Mesh mesh;
    private final Transform transform;
    private Material material;
    private boolean visible = true;
    private boolean collidable;
    private boolean staticBatchExcluded;

    public RenderObject(String id, Mesh mesh, Material material) {
        this(id, mesh, material, new Transform());
    }

    public RenderObject(String id, Mesh mesh, Material material, Transform transform) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Object id cannot be blank.");
        this.mesh = Objects.requireNonNull(mesh, "mesh");
        this.material = Objects.requireNonNull(material, "material");
        this.transform = Objects.requireNonNull(transform, "transform");
    }

    public String id() { return id; }
    public Mesh mesh() { return mesh; }
    public Transform transform() { return transform; }
    public Material material() { return material; }
    public RenderObject setMaterial(Material material) { this.material = Objects.requireNonNull(material, "material"); return this; }
    public boolean visible() { return visible; }
    public RenderObject setVisible(boolean visible) { this.visible = visible; return this; }
    /** Whether gameplay physics and ray queries should treat this object as solid. */
    public boolean collidable() { return collidable; }
    public RenderObject setCollidable(boolean collidable) { this.collidable = collidable; return this; }
    /** Keep special full-world/background objects out of static world batches and their lighting heuristics. */
    public boolean staticBatchExcluded() { return staticBatchExcluded; }
    public RenderObject setStaticBatchExcluded(boolean excluded) { this.staticBatchExcluded = excluded; return this; }
}
