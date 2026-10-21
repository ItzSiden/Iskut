# Iskut item assets

Each gameplay item still has one `.item` definition under a category folder. Its **only authored 3D visual asset** is an optional model named exactly after the definition ID:

`data/items/models/<item-id>.vmodel`

The engine automatically uses that one model for:

- generated transparent isometric inventory/hotbar/cursor/tooltip thumbnails;
- the first-person held item (for weapons, tools/consumables/materials/quest items);
- world item-drop visuals through `ItemWorldRenderSystem`.

No separate PNG icon or dedicated held model is required. The first run builds a cache under `data/cache/item-icons/`; editing the `.vmodel` regenerates the corresponding thumbnail. The cache is disposable and not source data. Items without a `.vmodel` safely fall back to the legacy short icon label.

## Current cuboid .vmodel format

One file can contain many named cuboid parts. Coordinates and dimensions are in world units; each part can rotate around Y in degrees. Use the same model syntax as entity models, with either `ISKUT_ITEM_MODEL 1`, `ISKUT_MODEL 1`, or the compatible `SKYVOXEL_ENTITY_MODEL 1` header.

```text
ISKUT_ITEM_MODEL 1
NAME training_blade
HEIGHT 0.92
PART pommel gold 0.00 -0.11 0.00 0.14 0.09 0.14 0
PART grip leather 0.00 0.035 0.00 0.105 0.27 0.105 -8
PART blade silver 0.00 0.515 0.00 0.145 0.59 0.055 0
```

Syntax: `PART <part-id> <material> <x> <y> <z> <width> <height> <depth> <rotationY-degrees>`.

Materials may use palette names such as `steel`, `silver`, `gold`, `leather`, `wood`, `red`, `blue`, `violet`, `cyan`, `white`, `black`, `stone`, or a custom RGB value such as `#35C6D8`. For weapon/tool models, place the local origin at the grip and extend the blade/tool along +Y. For a potion/material, place the origin near the model's centre. The thumbnail generator centres the visible geometry automatically, so the model origin can remain useful for holding.

The current `.vmodel` renderer uses reusable cuboid parts rather than arbitrary imported triangles. If the editor exports another grammar or arbitrary mesh format, add support to `EntityModelLoader` once for the whole catalog instead of adding per-item code.
