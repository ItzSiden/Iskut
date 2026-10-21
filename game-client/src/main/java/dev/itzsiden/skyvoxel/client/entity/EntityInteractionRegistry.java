package dev.itzsiden.skyvoxel.client.entity;

import dev.itzsiden.skyvoxel.engine.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Data-driven registry for entity prompts and E-key handlers (NPC dialogue, shops, portals, quests). */
public final class EntityInteractionRegistry {
    @FunctionalInterface
    public interface Handler {
        String interact(Entity entity);
    }

    public record Definition(String entityId, String prompt, Handler handler) {
        public Definition {
            if (entityId == null || entityId.isBlank()) throw new IllegalArgumentException("entityId cannot be blank.");
            if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt cannot be blank.");
            Objects.requireNonNull(handler, "handler");
        }
    }

    private final Map<String, Definition> byEntityId = new LinkedHashMap<>();

    public EntityInteractionRegistry register(String entityId, String prompt, Handler handler) {
        Definition definition = new Definition(entityId, prompt, handler);
        if (byEntityId.putIfAbsent(entityId, definition) != null) throw new IllegalArgumentException("Interaction already registered for entity: " + entityId);
        return this;
    }

    public Optional<Definition> definition(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return Optional.ofNullable(byEntityId.get(entity.id()));
    }

    public Optional<String> interact(Entity entity) {
        return definition(entity).map(definition -> {
            String message = definition.handler().interact(entity);
            return message == null ? "" : message;
        });
    }
}
