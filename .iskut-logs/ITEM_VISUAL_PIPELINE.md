# Iskut Item Visual Pipeline

## Authoring source

New item models use Blockbench's **Minecraft Java Block/Item** JSON export:

- Model: `data/items/models/<item-id>.json`
- Textures: `data/items/textures/<relative-texture-path>.png`
- Definition and gameplay metadata: `data/items/<category>/<item-id>.item`

The JSON filename stem maps to a stable `.item` ID. When both a JSON and legacy `.vmodel` exist for the same ID, JSON is preferred. `.vmodel` files remain as a migration fallback only. The starter catalogue currently has 22 Java JSON models with their texture PNGs.

## Runtime

- `JavaBlockModelLoader` loads Java Block/Item cuboid elements, texture aliases, face UV rectangles, UV rotations and element X/Y/Z rotations into the shared runtime model asset.
- `ItemVisualRegistry` chooses the JSON asset, caches the reusable model and generates inventory/hotbar/cursor/tooltip thumbnails.
- `HeldItemViewModel` displays the same model in first person. Keep a useful part name such as `grip`, `handle`, `hilt`, or `haft` when an item should be anchored by its handle.
- `ItemWorldRenderSystem` reuses the same geometry for world drops. Item visuals are non-collidable; collision is not generated for inventory or dropped item models.
- Model-local textures are packed into a padded nearest-filtered atlas at load time. Generated icons under `data/cache/item-icons/` are disposable.

## Format scope and limits

Use self-contained Java Block/Item JSON exports with an `elements` list and declared texture references. PNG textures may be rectangular and up to 512×512. Zero-thickness elements render as very thin visual slabs but are marked ineligible for structure collision. Parent-model inheritance and animated entity skeletons are not part of this importer; entity models remain on the separate `.vmodel` pipeline for now.

## Block models inside structures

Place block/prop JSON models under `data/blocks/models/` and textures under `data/blocks/textures/`. A `.vstruct` can place one with:

```text
MODEL model_id ../blocks/models/model.json x y z scale true|false [localYawDegrees]
```

The final boolean controls structure collision. The `.vmap` remains the whole-world layout and only references reusable `.vstruct` files under `data/structures/`.
