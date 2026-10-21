package dev.itzsiden.skyvoxel.engine.scene;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Main-thread scene container with stable object IDs and deterministic iteration order. */
public final class Scene {
    private final Map<String, RenderObject> objectsById = new LinkedHashMap<>();
    private final Collection<RenderObject> objectsView = Collections.unmodifiableCollection(objectsById.values());

    public Scene add(RenderObject object) {
        Objects.requireNonNull(object, "object");
        if (objectsById.putIfAbsent(object.id(), object) != null) {
            throw new IllegalArgumentException("Duplicate scene object id: " + object.id());
        }
        return this;
    }

    public RenderObject remove(String id) { return objectsById.remove(id); }
    public RenderObject get(String id) { return objectsById.get(id); }
    public int size() { return objectsById.size(); }
    /** Read-only live view. Scene modification and rendering must happen on the game thread. */
    public Collection<RenderObject> objects() { return objectsView; }
}
