# Iskut Ocean World Data Update (Current)

The active map is `data/maps/main_hub.vmap`. It is the world layout and places reusable `.vstruct` files from `data/structures/`.

- `data/structures/ocean_surface.vstruct` creates a single top-facing 1024×1024 plane at world Y=1.0.
- The water plane has exactly two triangles, zero thickness and collision disabled.
- It is excluded from static batching and from AO/coplanar offset bounds, so a world-scale water mesh cannot modify neighboring object shading.
- Existing island structures and spawn are translated by +1.18 so their height relationship to the sea is preserved.
- `PlayerController` respawns players below Y=-0.47; there is no global hidden floor.
- The beach uses collidable dry/wet sand and visual-only shallow-water/foam bands.

Run `OceanWorldSmokeTest` for sea level, mesh topology, render-batcher isolation, collision exclusion, shoreline material bands and spawn support. See `.iskut-logs/OCEAN_SHORELINE_PHYSICS.md` for the current geometry profile.
