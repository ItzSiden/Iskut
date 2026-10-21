# Iskut item visual pipeline

## One authored model, multiple runtime views

`data/items/models/<item-id>.vmodel` is the single authored visual source. Item metadata, rarity, stats, and stack behaviour remain in `.item` definitions.

- `EntityModelLoader` accepts legacy flat-color v1 and self-contained pixel-textured v2 model files.
- A v2 model embeds indexed pixel textures (palette + rows) and per-cuboid-face texture/rotation/mirror mappings. It is parsed once at startup and packed into one small, nearest-filtered atlas per model.
- `ItemVisualRegistry` loads model files directly in `data/items/models/`, maps filenames to stable item IDs, and caches the generated icon by content hash plus generator version.
- `ItemIconGenerator` renders textured model faces into a transparent 64×64 isometric PNG.
- `InventoryOverlay` draws generated images in storage/equipment/hotbar slots, the cursor stack, and the item details preview.
- `HeldItemViewModel` renders the selected item model as camera-relative 3D geometry alongside Iskut's segmented Arc-Guard forearm. If a held model has a `grip`, `handle`, `hilt`, or `haft` part, that part is used as the hand anchor, rather than assuming the model origin is the grip.
- `ItemWorldRenderSystem` instantiates the same model in a scene for dropped/pickup visuals.

No source PNG is required for a v2 model. The generated PNG and cache signature under `data/cache/item-icons/` are disposable build products. A `.vmodel` file content change invalidates them.

## v2 format

See `EDITOR_VMODEL_TEXTURE_AUTHORING_SPEC.md` for the exact grammar and the Three.js editor implementation checklist. In brief:

- `TEXTURE`, `PALETTE`, `PIXEL`, and `END_TEXTURE` embed indexed pixel artwork.
- `PART` describes named cuboids.
- `FACE` maps a texture to one of `TOP`, `BOTTOM`, `FRONT`, `BACK`, `LEFT`, `RIGHT`, optionally with 90-degree rotation and U/V mirror flags.
- Textures are packed into a padded per-model atlas at load time. Per-face mesh UVs reference the relevant atlas region.
- Legacy v1 material-only models are still accepted.

This runtime is deliberately cuboid-first. Arbitrary triangle meshes are not part of v2; a future model format can extend the shared loader once without adding per-item rendering branches.

## Current textured starter assets

All 22 starter `.item` definitions have corresponding `.vmodel` files with embedded pixel textures. World structure materials are a separate shared library under `data/textures/world/`, referenced by material IDs from `.vstruct` cuboids. See `WORLD_TEXTURES_AND_VIEWMODEL.md` for the asset list and world texture tiling conventions.
