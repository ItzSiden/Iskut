package dev.itzsiden.skyvoxel.client.items;

import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared .vmodel-backed world-drop renderer. Gameplay systems can spawn/remove visual item instances by stable ID. */
public final class ItemWorldRenderSystem {
    private record PartBinding(EntityModel.Part part, RenderObject object) { }
    private static final class Drop {
        final String instanceId, itemId;
        final float x, y, z, scale;
        final List<PartBinding> parts;
        float yaw;
        Drop(String instanceId, String itemId, float x, float y, float z, float scale, List<PartBinding> parts) {
            this.instanceId = instanceId; this.itemId = itemId; this.x = x; this.y = y; this.z = z; this.scale = scale; this.parts = parts;
        }
    }

    private final ItemVisualRegistry visuals;
    private final Scene scene = new Scene();
    private final Map<String, Drop> drops = new LinkedHashMap<>();
    private float timeSeconds;

    public ItemWorldRenderSystem(ItemVisualRegistry visuals) { this.visuals = Objects.requireNonNull(visuals, "visuals"); }
    public Scene scene() { return scene; }
    public int size() { return drops.size(); }

    public boolean spawn(String instanceId, String itemId, float x, float y, float z, float scale) {
        if (instanceId == null || instanceId.isBlank() || drops.containsKey(instanceId)) return false;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(scale) || scale <= 0) return false;
        EntityModel model = visuals.model(itemId).orElse(null);
        if (model == null) return false;
        List<PartBinding> parts = new ArrayList<>();
        int index = 0;
        for (EntityModel.Part part : model.parts()) {
            String objectId = "item-drop/" + instanceId + "/" + index++ + "/" + part.id();
            RenderObject object = new RenderObject(objectId, part.mesh(), part.material(), new Transform()).setCollidable(false);
            scene.add(object);
            parts.add(new PartBinding(part, object));
        }
        drops.put(instanceId, new Drop(instanceId, itemId, x, y, z, scale, List.copyOf(parts)));
        return true;
    }

    public boolean remove(String instanceId) {
        Drop drop = drops.remove(instanceId);
        if (drop == null) return false;
        for (PartBinding part : drop.parts) scene.remove(part.object().id());
        return true;
    }

    public void update(float deltaSeconds) {
        float dt = Math.max(0.0f, Math.min(deltaSeconds, 0.1f));
        timeSeconds += dt;
        for (Drop drop : drops.values()) {
            drop.yaw += dt * 0.75f;
            float bob = (float) Math.sin(timeSeconds * 2.4f + stablePhase(drop.instanceId)) * 0.055f;
            float cos = (float) Math.cos(drop.yaw), sin = (float) Math.sin(drop.yaw);
            for (PartBinding binding : drop.parts) {
                EntityModel.Part p = binding.part;
                float wx = drop.x + (p.offsetX() * cos + p.offsetZ() * sin) * drop.scale;
                float wz = drop.z + (-p.offsetX() * sin + p.offsetZ() * cos) * drop.scale;
                binding.object.transform().setPosition(wx, drop.y + bob + p.offsetY() * drop.scale, wz)
                    .setRotation(0, drop.yaw + p.rotationY(), 0)
                    .setScale(p.width() * drop.scale, p.height() * drop.scale, p.depth() * drop.scale);
            }
        }
    }

    private static float stablePhase(String id) { return (id.hashCode() & 0x7fffffff) % 6283 / 1000.0f; }
}
