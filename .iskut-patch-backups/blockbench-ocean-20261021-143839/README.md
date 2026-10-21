# Iskut

Custom Java + LWJGL game engine prototype with a backend-neutral renderer API, optimized software renderer, handcrafted `.vmap` worlds, `.vstruct` structures, a `.vmodel` entity pipeline, skybox, and the first playable gameplay foundation.

## Run the hub

Run `run-hub.bat` from this folder. It starts the handcrafted map, not the procedural renderer demo.

The client opens with a loading screen, followed by the main menu. Select **Play** to enter the
world; press **Esc** while playing to open the pause menu. Resume with **Resume** or **Esc**, open
client settings, or return to the main menu. Use **Quit** on the main menu to exit.
The loading, main-menu, pause-menu, and settings interfaces are separate Java classes in
`game-client/src/main/java/dev/itzsiden/skyvoxel/client/ui/` for straightforward future refactoring.

## Current systems

- Renderer interface separated from rendering backends; software rendering remains the low-end fallback.
- Hand-authored map/structure format and reusable cuboid models.
- Skybox gradient, sun, and matching fog.
- Entity registry/rendering/holograms with model-derived hitboxes.
- Gravity, jump buffering/coyote time, sprint, crouch, fall damage, static and entity collision.
- NPC interactions, player profile panel, hostile mob combat, HP/mana/defense/strength/intelligence, equipment slots, accessories, pet buffs, and profile snapshots.

Read `GAMEPLAY_FOUNDATION.md` for controls, current tuning, system APIs, verification coverage, and the honest list of still-unimplemented MMORPG features.


## UI font (optional)

Iskut uses **Inter** for clean, readable game UI text. Place the regular Inter TrueType/OpenType font at `data/fonts/Inter.ttf`. Supported formats are `.ttf` and `.otf`; `.obb`, `.woff`, and `.woff2` are not accepted by the Java desktop font loader. If the file is absent or invalid, the game falls back to the system SansSerif font without failing to launch.

The HUD uses a compact dark surface, restrained cyan accent, context prompts, and Health/Mana bars. Press `F3` to show/hide developer diagnostics, or press `F1` while playing to save a PNG screenshot (including the HUD) to `screenshots/`. Entity nameplates are anchored to each model's actual highest part and reprojected every frame.

## Inventory, item definitions and stat engine

- Press `I` for the inventory; `Esc` closes it before opening the pause menu.
- `1`–`9` select hotbar slots; `R` uses the selected hotbar item when it has a supported effect. Left click picks up/places/merges/swaps; right click splits or places one; Shift-click quick-moves between backpack/hotbar or equips compatible gear.
- The data-driven item catalog lives in `data/items/`. Create one `.item` file per item. The runtime stores stable IDs and quantities, not copied item metadata.
- The stat system aggregates flat, percentage and multiplier modifiers from named sources, so equipment, pets, temporary buffs and area effects can be added/removed independently.
- Player state saves automatically to `saves/players/local-player.json`; it also saves on normal shutdown. Previous data is backed up as `.bak`. Use `--player-id=Name` for another profile or `--save-dir=<directory>` to customize storage.
- Persistence is behind `PlayerDataRepository`, so a database backend can replace file storage later without making inventory/combat code depend on JSON. See `PLAYER_DATA_PERSISTENCE.md` for the save schema and database migration approach.
- See `INVENTORY_ITEMS_STATS.md` for the item file format, architecture and tests.

## Self-contained pixel-textured models (`.vmodel` v2)

Model files can embed both cuboid geometry and per-face pixel artwork. New item models should use `ISKUT_ITEM_MODEL 2`; entity models can use `SKYVOXEL_ENTITY_MODEL 2` with the same texture grammar. Existing v1 flat-color models continue to load. Each v2 file contains indexed `TEXTURE`/`PALETTE`/`PIXEL` blocks plus `PART` and optional `FACE` mappings; the runtime builds a padded nearest-filtered atlas at startup. Inventory thumbnails, held items, and world drops all use the same model source. The current starter catalogue has 22/22 matching textured model files.

The hub now loads shared seamless pixel-art textures from `data/textures/world/` and repeats UVs across larger surfaces, instead of stretching one flat colour over every cuboid. The current hub uses 19 shared world material textures across 164 authored cuboids/props, including the portal, shop stalls, garden structures, floating horizon isles and layered clouds. `.vstruct` material IDs remain backward-compatible; the editor can reference those IDs from a shared world material palette.

For held weapons/tools, name the intended grip part `grip`, `handle`, `hilt`, or `haft`: the camera-relative viewmodel automatically anchors that part inside the custom Iskut Arc-Guard hand. This is more reliable than assuming every model origin is at the grip.

**For updating the Three.js editor, read `EDITOR_VMODEL_TEXTURE_AUTHORING_SPEC.md` and `WORLD_TEXTURES_AND_VIEWMODEL.md`.** They give the exact model grammar, face axis conventions, rotation/mirror rules, grip naming convention, world material IDs, import/export behaviour, validation requirements, and a checklist for a Blockbench-like pixel painting workspace. No source PNG is required for item thumbnails or held models; world material PNGs are shared data assets.


## Visual feel and player physics

- The normal hub launcher enables `--quality=smooth` for interpolated vertex lighting, and uses a 0.90 render scale with bilinear presentation for a softer image. Static map objects render in 53 material/chunk batches instead of submitting all 164 authored objects separately. Use `--quality=fast --render-scale=0.95` if crisp pixels and maximum FPS matter more.
- The software window bilinearly scales the final framebuffer while keeping UI text rasterized at window resolution. Pixel textures remain nearest-sampled inside the renderer.
- World and item cuboids use warmer directional face shading, per-vertex corner occlusion and depth-based smooth fog (not polygon-flat haze bands).
- Grounded movement automatically steps up floor-connected cuboids up to 1.0 world unit if full body clearance is clear. Tall walls, floating blocks and steps blocked by ceilings are not auto-climbed. Landing and step-up camera effects are damped to avoid harsh vertical snaps.
- Physics remains AABB-based. Smooth triangular ramps/sloped mesh collision and a full capsule controller are not implemented yet; small stair-like slopes made from short cuboids are supported.


## Ocean World / Shoreline Physics

The current main hub is an authored island in a continuous ocean. Sea level is `Y=-0.18`; the island has warm dirt sides, a grass cap, a walkable dithered shoreline lip, stepped dry/wet sand, shallow turquoise water and a broken foam edge. The shared ocean texture is finer-grained to reduce the obvious large repeating water tiles.

Player gravity no longer uses an invisible global `Y=0` floor. Only actual collidable map surfaces support the player. Falling off the island continues under gravity; passing below `Y=-1.65` triggers the lost-at-sea respawn flow. See `OCEAN_SHORELINE_PHYSICS.md` for surface heights and asset details.

## Ocean World / Beach Physics Update

The active `data/maps/main_hub.vmap` is now an ocean-side island hub, without the former ghost floating-island/cloud placements. The map references `ocean_surface.vstruct` and `beach_transition.vstruct`; the latter provides a walkable shore lip, dry sand, wet sand, shallow-water tint and a broken foam ribbon. Shared materials live under `data/textures/world/`.

Player support comes only from real collidable map geometry. The ocean is non-collidable; leaving the island now continues through gravity and triggers a `Lost at sea` respawn rather than standing on a hidden global floor. The flat 1024-unit water plane uses a coarse static mesh (576 triangles) to avoid unnecessary subdivision. See `OCEAN_SHORELINE_PHYSICS.md` for levels, material bands and test coverage.
