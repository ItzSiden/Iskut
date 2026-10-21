# Iskut: Textured Items, World Materials, and First-Person Viewmodel

This update closes the first runtime texture gap: every starter item definition now has a matching textured `.vmodel`, every material used by the current hub has a pixel texture, and the first-person weapon is aligned by its semantic grip part.

## Item visual source of truth

- `data/items/*.item` remains the gameplay definition: ID, rarity, category, stats, stack rules, and equipment slot.
- `data/items/models/<exact-item-id>.vmodel` contains the single model asset: cuboids plus embedded v2 pixel textures and face mappings.
- All 22 starter item definitions currently have a matching `.vmodel`. The registry logs the model count at startup and generates the 64×64 inventory/hotbar icons from those files.
- The same loaded model/texture atlas is reused for first-person held items and world drops. Do not hand-create per-item PNG icons or duplicate held models.
- Icon files under `data/cache/item-icons/` are generated cache, not authored assets. If a vmodel changes, the cache signature changes and the icon regenerates.

## First-person grip convention

- The viewmodel uses the active camera basis each frame; it should remain fixed relative to the screen while the player turns or looks up/down.
- The Iskut Arc-Guard arm is a custom segmented, cuboid-first forearm and glove. It is not a Minecraft arm mesh.
- For held items, the runtime searches model part IDs for `grip`, `handle`, `hilt`, or `haft` (case-insensitive). If found, that part's centre is anchored to the hand grip; all other parts are positioned relative to it.
- Name the part that should sit inside the hand `grip` for swords, `handle` for tools, or `hilt`/`haft` as appropriate. Keep the model's visual geometry oriented so the blade/tool extends along local +Y for the current default weapon pose. The model origin no longer has to be exactly at the grip when a named grip part exists.
- If no semantic anchor is found, the viewmodel falls back to the model origin and generic item pose. Consumables/materials without a grip are therefore still displayable; highly custom weapons should name the grip part.

## World materials

`data/textures/world/` contains 19 shared seamless pixel-art PNG materials:

`stone`, `stone_dark`, `stone_light`, `slate`, `grass`, `moss`, `wood`, `wood_light`, `brick`, `roof_blue`, `roof_red`, `gold`, `crystal`, `cyan`, `violet`, `water`, `leaves`, `sand`, and `white`.

The map loader loads the library once per map and shares each `Material`/`TextureData` across objects. `.vstruct` files continue to reference material IDs in their existing `CUBOID` directives. The current hub uses 17 distinct textures across 115 cuboids. There is no need to embed the same structural texture in every cuboid. The shared textures are authored files and must remain present under `data/textures/world/`.

World cuboids use repeatable UVs: the current target is one texture tile per 1.6 world units along the surface, rather than a single stretched pixel tile across a whole wall or floor. Side faces keep inexpensive baked directional shading. Texture atlas/UV repeat support should retain low-resolution pixel artwork and avoid per-object texture duplicates.

## Files an editor update should respect

- Item geometry/texture format: `EDITOR_VMODEL_TEXTURE_AUTHORING_SPEC.md`.
- World texture assets and material IDs: `data/textures/world/<material-id>.png`.
- Structural material assignment is the existing material ID in `.vstruct`; no `.vstruct` version migration is needed for this change.

For the Three.js editor's Blockbench-like texture painter, implement vmodel v2 import/export from the spec, plus an independent world material picker/preview that references the world texture IDs above. Do not force a per-object copy of the shared world PNG into every model.

## Verification

- Textured vmodel parser: alpha cutout, face UV, cache regeneration, v1 compatibility, invalid rows rejected.
- Current starter catalogue: 22 item definitions → 22 vmodel assets → 22 generated thumbnails.
- Current hub: 115/115 cuboids have textured materials; 17 shared material IDs are used; large meshes repeat UVs.
- First-person pose: grip-part anchoring plus a camera-space transform; automated yaw/pitch stability test compares local pose before/after camera turns.
- Previous inventory persistence, movement/combat, and hologram regression tests pass.

The build in this environment compiled the renderer API, engine core, UI core, software renderer, and game client with Java 21. OpenGL compilation/runtime still needs the normal Windows Gradle build because LWJGL dependencies are not installed in this test environment.
