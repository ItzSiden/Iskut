# Iskut update: skybox + entity system

## Run

Run `run-hub.bat` from the project root (same as before). The regular handcrafted `.vmap` remains the active map; `--voxel` is still only an opt-in benchmark/demo.

## Skybox and atmosphere

- Backend-neutral `SkyboxSettings` is attached to `RenderFrame`.
- Software renderer fills a fast per-row gradient background and projects a sun disc using the camera view-projection matrix.
- OpenGL 2.1 compatibility backend draws the same style as a gradient quad plus lightweight sun halo; no extra textures or shaders are required.
- Fog tint matches the sky's atmospheric horizon to make distance fade less like a flat clear colour.
- Existing material/mesh face shades remain baked and shared across both renderer backends, so no expensive per-pixel dynamic lighting was introduced.

## Entity architecture

`engine-core/entity` contains `Entity`, `EntityType`, `EntityModel`, `EntityWorld` and `EntityRenderSystem`.

- Entities use stable IDs, mutable position/yaw/health/display name and visibility flags.
- `.vmodel` files define reusable cuboid parts relative to the feet origin. Every part references a material and local transform.
- Many entity instances can share the same model and unit-cuboid mesh. Entity parts are adapted to ordinary render objects, reusing SceneRenderer's mesh cache and frustum culling.
- Holograms are camera-facing screen-space UI: subtitle/tag, display name and optional health bar. This is the same route that can show player tags for remote players replicated by a future game server.
- The local first-person player exists in EntityWorld but its model/nameplate are hidden.
- NPC and remote-player entities in this build are showcase records; the Sky Wisp has a tiny floating/turning idle animation. AI, combat, entity collisions and network replication are not implemented yet.

## Data

`data/entities/models/*.vmodel` are text model definitions. Current directives:

```
SKYVOXEL_ENTITY_MODEL 1
NAME model_name
HEIGHT 1.8
PART part_id material x y z width height depth rotationYDegrees
```

Materials currently supported by `EntityModelLoader`: navy, blue, teal, skin, dark, gold, violet, cyan, white, red, leaf, brown.

## Performance notes

The entity render system creates scene objects once per entity/model and updates transforms on the game thread. Static and entity scenes use the same backend-neutral renderer and culling path. Nameplate candidates are distance and clip-space culled before adding 2D commands. Models intentionally share one unit cuboid mesh. On the low-end software backend, entity counts should remain modest and be benchmarked as content grows.
