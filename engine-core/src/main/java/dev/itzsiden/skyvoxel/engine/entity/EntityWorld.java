package dev.itzsiden.skyvoxel.engine.entity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map;

/** Entity registry with stable IDs. Mutate only on the game thread; network packets should be queued. */
public final class EntityWorld {
    private final Map<String, Entity> byId = new LinkedHashMap<>();
    private final Collection<Entity> view = Collections.unmodifiableCollection(byId.values());

    public EntityWorld add(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (byId.putIfAbsent(entity.id(), entity) != null) throw new IllegalArgumentException("Duplicate entity id: " + entity.id());
        return this;
    }
    public Entity remove(String id) { return byId.remove(id); }
    public Entity get(String id) { return byId.get(id); }

    /** Apply an authoritative snapshot on the game thread using a preloaded model registry. */
    public Entity applySnapshot(EntitySnapshot snapshot, Map<String, EntityModel> models) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(models, "models");
        EntityModel model = models.get(snapshot.modelId());
        if (model == null) throw new IllegalArgumentException("Unknown replicated entity model: " + snapshot.modelId());
        Entity entity = byId.get(snapshot.id());
        if (entity == null || entity.type() != snapshot.type() || entity.model() != model) {
            if (entity != null) byId.remove(snapshot.id());
            entity = new Entity(snapshot.id(), snapshot.type(), model, snapshot.displayName(), snapshot.subtitle());
            byId.put(entity.id(), entity);
        }
        entity.setDisplayName(snapshot.displayName()).setSubtitle(snapshot.subtitle())
            .setPosition(snapshot.x(), snapshot.y(), snapshot.z()).setYaw(snapshot.yaw())
            .setHealth(snapshot.health(), snapshot.maxHealth()).setDefense(snapshot.defense());
        return entity;
    }
    public int size() { return byId.size(); }
    public Collection<Entity> entities() { return view; }
    public long count(EntityType type) { return byId.values().stream().filter(e -> e.type() == type).count(); }
}
