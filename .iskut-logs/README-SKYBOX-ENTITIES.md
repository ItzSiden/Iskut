# Iskut: Skybox + Entity System update

## Run
Run `run-hub.bat` from the project root. It loads the hand-authored `data/maps/main_hub.vmap`; terrain generation is not enabled by default.

## New skybox
- `SkyboxSettings` is renderer-API data carried by `RenderFrame`, so either backend can use the same sky config.
- Software renderer draws a per-row top/horizon/lower sky gradient plus a projected, cheap sun disc and halo.
- OpenGL 2.1 compatibility renderer draws the gradient with fixed-pipeline screen-space primitives, then restores the scene state; no new shader or texture is required.
- Fog matches the skybox horizon colour to create atmospheric depth.
- World and entity cuboid meshes keep their cheap baked per-face directional shading. This update avoids expensive per-pixel lighting/shadows so the older CPU/GPU budget remains reasonable.

## Entity architecture
`engine-core/entity` adds `Entity`, `EntityType`, `EntityModel`, `EntityWorld`, `EntitySnapshot`, and `EntityRenderSystem`.
- Stable IDs and type categories: PLAYER, MOB, NPC, PET, ITEM.
- Models are reusable `.vmodel` templates made from named cuboid parts with local offsets, dimensions and rotation. Many entity instances reuse the same unit-cuboid mesh.
- Each entity has transform, display name/subtitle, health, model/nameplate visibility state.
- `EntityRenderSystem` converts model parts to ordinary render objects, so entities share `SceneRenderer`, its mesh cache and frustum culling.
- Holograms use the existing 2D draw list: type-coloured subtitle/tag, name, optional health bar. Projected labels are distance/frustum culled and refresh at 20 Hz to avoid a native-resolution software UI raster on every frame. Collidable world geometry can occlude labels.
- `EntityWorld.applySnapshot(EntitySnapshot, modelRegistry)` establishes a direct future network-replication hook. A server can send a snapshot; the game thread applies it to an Entity, and the normal entity render path displays it.
- The local first-person entity is in EntityWorld but its model/nameplate is hidden. Demo showcase entities are Astra (remote-player example), Elyra (guide NPC), and Sky Wisp (mob with health).

## Model format
Files live in `data/entities/models/*.vmodel`:
```text
SKYVOXEL_ENTITY_MODEL 1
NAME sky_wisp
HEIGHT 1.35
PART core violet 0 0.58 0 0.78 0.70 0.72 0
```
`PART` fields: `partId material offsetX offsetY offsetZ width height depth rotationYDegrees`. Currently available materials: navy, blue, teal, skin, dark, gold, violet, cyan, white, red, leaf, brown.

## Gameplay foundation update
See [GAMEPLAY_FOUNDATION.md](GAMEPLAY_FOUNDATION.md) for the new physics, entity collision/targeting, NPC interaction registry, hostile-mob combat, player stats, armor/accessory/pet loadouts, and player profile data model.

## Remaining limitations
- The current client uses local demo entities and local combat simulation. No authoritative multiplayer server or network transport is included yet.
- Inventory/equipment UI, full item catalog, loot, persistent progression, quests, skills/spells, pathfinding, slopes, ladders, and dynamic shadows remain future work.
- Mob AI is intentionally a small aggro/chase/melee/respawn demonstration, not a general navigation system.
- Entity targeting and physics use conservative AABBs, particularly for rotated model parts.
