package dev.itzsiden.skyvoxel.engine.entity;

import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converts reusable entity models into a normal Scene so the same renderer/culling/cache handles mobs and players. */
public final class EntityRenderSystem {
    private record PartBinding(EntityModel.Part part, RenderObject object) { }
    private record Binding(EntityModel model, List<PartBinding> parts) { }

    private final Scene scene = new Scene();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();

    public Scene scene() { return scene; }

    /** Synchronizes render objects to authoritative game-thread entity state. */
    public void sync(EntityWorld world) {
        Objects.requireNonNull(world, "world");
        Set<String> live = new HashSet<>();
        for (Entity entity : world.entities()) {
            live.add(entity.id());
            Binding binding = bindings.get(entity.id());
            if (binding == null || binding.model() != entity.model()) {
                if (binding != null) removeBinding(entity.id(), binding);
                binding = createBinding(entity);
                bindings.put(entity.id(), binding);
            }
            float yaw = entity.yaw();
            float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
            for (PartBinding partBinding : binding.parts()) {
                EntityModel.Part part = partBinding.part();
                RenderObject object = partBinding.object();
                float wx = entity.x() + part.offsetX() * cos + part.offsetZ() * sin;
                float wz = entity.z() - part.offsetX() * sin + part.offsetZ() * cos;
                object.transform().setPosition(wx, entity.y() + part.offsetY(), wz)
                    .setRotation(0, yaw + part.rotationY(), 0)
                    .setScale(part.width(), part.height(), part.depth());
                object.setVisible(entity.modelVisible());
            }
        }
        List<String> removed = new ArrayList<>();
        for (String id : bindings.keySet()) if (!live.contains(id)) removed.add(id);
        for (String id : removed) removeBinding(id, bindings.remove(id));
    }

    private Binding createBinding(Entity entity) {
        List<PartBinding> parts = new ArrayList<>();
        int partIndex = 0;
        for (EntityModel.Part part : entity.model().parts()) {
            String objectId = "entity/" + entity.id() + "/" + part.id();
            RenderObject object = new RenderObject(objectId, part.mesh(), part.material(), new Transform())
                .setCollidable(false);
            scene.add(object);
            parts.add(new PartBinding(part, object));
            partIndex++;
        }
        return new Binding(entity.model(), List.copyOf(parts));
    }

    private void removeBinding(String entityId, Binding binding) {
        if (binding == null) return;
        for (PartBinding part : binding.parts()) scene.remove(part.object().id());
    }
}
