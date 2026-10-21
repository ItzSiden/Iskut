package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.physics.RaycastHit;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Crosshair ray selection with hover tint and a persistent click selection. */
final class WorldTargeting {
    private static final double REACH = 6.0;
    private static final Material HOVER_MATERIAL = new Material("target-hover", 1.0f, 0.88f, 0.28f);
    private static final Material SELECTED_MATERIAL = new Material("target-selected", 0.35f, 0.94f, 1.0f);

    private final StaticCollisionWorld collisionWorld;
    private final Map<RenderObject, Material> originalMaterials = new IdentityHashMap<>();
    private RenderObject target;
    private RenderObject selected;

    WorldTargeting(Scene scene, StaticCollisionWorld collisionWorld) {
        this.collisionWorld = collisionWorld;
        for (RenderObject object : scene.objects()) originalMaterials.put(object, object.material());
    }

    Optional<RaycastHit> update(double ox, double oy, double oz, double dx, double dy, double dz,
                                boolean leftPressed, boolean rightPressed) {
        RenderObject oldTarget = target;
        RenderObject oldSelected = selected;
        RaycastHit hit = collisionWorld.raycast(ox, oy, oz, dx, dy, dz, REACH).orElse(null);
        target = hit == null ? null : hit.object();

        if (rightPressed) selected = null;
        else if (leftPressed && target != null) {
            selected = target;
            System.out.println("[Iskut] Selected object: " + selected.id()
                + (hit == null ? "" : String.format(java.util.Locale.ROOT, " (%.2f units)", hit.distance())));
        }

        if (oldTarget != null && oldTarget != target && oldTarget != selected) restore(oldTarget);
        if (oldSelected != null && oldSelected != selected && oldSelected != target) restore(oldSelected);
        if (target != null) target.setMaterial(target == selected ? SELECTED_MATERIAL : HOVER_MATERIAL);
        if (selected != null) selected.setMaterial(SELECTED_MATERIAL);
        return Optional.ofNullable(hit);
    }


    RenderObject target() { return target; }

    /** Forget an object removed from the live scene so stale hover/selection state cannot remain. */
    void forget(RenderObject object) {
        if (object == null) return;
        restore(object);
        originalMaterials.remove(object);
        if (target == object) target = null;
        if (selected == object) selected = null;
    }

    String status(Optional<RaycastHit> hit) {
        StringBuilder text = new StringBuilder();
        if (hit.isPresent()) {
            RaycastHit result = hit.get();
            text.append("Target: ").append(result.object().id())
                .append(String.format(java.util.Locale.ROOT, "  |  %.2f m", result.distance()));
        }
        if (selected != null) {
            if (!text.isEmpty()) text.append("  |  ");
            text.append("Selected: ").append(selected.id());
        }
        return text.toString();
    }

    private void restore(RenderObject object) {
        Material original = originalMaterials.get(object);
        if (original != null) object.setMaterial(original);
    }
}
