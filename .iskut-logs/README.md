# Iskut

Custom Java + LWJGL game engine prototype with a backend-neutral renderer API, optimized software renderer, handcrafted `.vmap` world layouts, reusable `.vstruct` structures, Blockbench Java Block/Item models, a separate entity-model pipeline, skybox, and the first playable gameplay foundation.

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

## Blockbench models: Blocks, Items and Structures

- **Authoring:** use Blockbench and export **Minecraft Java Block/Item** JSON for blocks and items. This model loader supports cuboid elements, face texture references, per-face UV rectangles/rotations, element rotations and PNG textures. Rectangular PNGs up to 512×512 are supported.
- **Items:** place `<item-id>.json` in `data/items/models/` and referenced PNGs in `data/items/textures/`. JSON is authoritative when both a JSON and legacy `.vmodel` exist. The starter catalogue has 22 Java JSON models; `data/items/models/*.vmodel` remains only as a backwards-compatible migration fallback. Item models are visual-only: their held view, world drops and generated inventory thumbnails use the same asset, without physics collision.
- **Blocks and props:** put Java model JSON in `data/blocks/models/` and textures in `data/blocks/textures/`. A plain texture name such as `stone` can fall back to `data/textures/world/stone.png`. For solid props, place the model through a `.vstruct` `MODEL` directive; collision comes from eligible cuboid elements, not from inventory item models. Zero-thickness elements are rendered as paper-thin visual geometry and are not collidable.
- **World layout:** `data/maps/main_hub.vmap` only defines the spawn and `PLACE` references to reusable `.vstruct` files. `data/structures/` contains the placeable world structures. Existing `CUBOID` structure entries remain useful for long terrain slabs and tiled coast geometry; `MODEL` entries embed Blockbench-exported details. `.vmap` is the layout, `.vstruct` is the reusable structure, and `.json` is an individual Blockbench model.
- **Entities:** entity models remain on their separate `.vmodel` path for now. Skeletal entity export/animation is a later migration, rather than mixing Java Block/Item JSON with entity bones prematurely.

Example structure directive:

```text
MODEL lantern ../blocks/models/camping_lantern.json 0 0.5 0 1 true
```

The placement coordinates are relative to the model's `[8, 8, 8]` origin. The final `true` enables per-cuboid collision for that placed structure. Use `false` for purely decorative structures. See `data/blocks/models/README.md` for the complete asset conventions.

## Visual feel and player physics

- The normal hub launcher enables `--quality=smooth` for interpolated vertex lighting, and uses a 0.90 render scale with bilinear presentation for a softer image. Static map objects render in 53 material/chunk batches instead of submitting all 164 authored objects separately. Use `--quality=fast --render-scale=0.95` if crisp pixels and maximum FPS matter more.
- The software window bilinearly scales the final framebuffer while keeping UI text rasterized at window resolution. Pixel textures remain nearest-sampled inside the renderer.
- World and item cuboids use warmer directional face shading, per-vertex corner occlusion and depth-based smooth fog (not polygon-flat haze bands).
- Grounded movement automatically steps up floor-connected cuboids up to 0.6 world unit if full body clearance is clear. Full blocks, tall walls, floating blocks and steps blocked by ceilings require jumping or another route. Landing and step-up camera effects are damped to avoid harsh vertical snaps.
- Physics remains AABB-based. Smooth triangular ramps/sloped mesh collision and a full capsule controller are not implemented yet; small stair-like slopes made from short cuboids are supported.


## Ocean World / Shoreline Physics

The current main hub is an authored island in a continuous ocean. Sea level is exactly `Y=1.0`; the ocean is a single top-facing 1024×1024 world plane with two triangles. It is non-collidable and excluded from static terrain batching, ambient-occlusion queries and coplanar-offset calculations. The authored island structures and spawn are shifted by +1.18 world units to preserve their layout and shoreline profile.

Player gravity has no invisible global floor. Only real collidable map surfaces support the player. Falling below `Y=-0.47` triggers the lost-at-sea respawn flow. See `.iskut-logs/OCEAN_SHORELINE_PHYSICS.md` for exact material levels and regression coverage.

## Ocean World / Beach Physics Update

The active `data/maps/main_hub.vmap` is now an ocean-side island hub, without the former ghost floating-island/cloud placements. The map references `ocean_surface.vstruct` and `beach_transition.vstruct`; the latter provides a walkable shore lip, dry sand, wet sand, shallow-water tint and a broken foam ribbon. Shared materials live under `data/textures/world/`.

Player support comes only from real collidable map geometry. The ocean is non-collidable; leaving the island now continues through gravity and triggers a `Lost at sea` respawn rather than standing on a hidden global floor. The water plane is a single two-triangle surface that is excluded from world AO/coplanar processing, so it cannot become a collider or perturb neighboring terrain shading. See `OCEAN_SHORELINE_PHYSICS.md` for levels, material bands and test coverage.
