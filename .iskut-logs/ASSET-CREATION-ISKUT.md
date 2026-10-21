# Iskut Asset Creation (Current)

## Current asset format policy

- **Items:** Blockbench `Minecraft Java Block/Item` JSON under `data/items/models/<item-id>.json`; put referenced PNGs under `data/items/textures/`. The filename stem must match the `.item` definition ID. Inventory icons are generated from the model. Held and dropped items reuse the same model and do not participate in collision.
- **Blocks and placeable props:** Blockbench `Minecraft Java Block/Item` JSON under `data/blocks/models/`; put referenced PNGs under `data/blocks/textures/`. A texture named `stone` may use the shared fallback at `data/textures/world/stone.png`.
- **World layout:** `data/maps/<map>.vmap` defines `MAP`, `SPAWN`, and ordered `PLACE` references. Keep actual reusable structures under `data/structures/*.vstruct`.
- **Structures:** `.vstruct` supports `CUBOID id material x y z width height depth solid [yawDegrees]`, `PLANE id material x y z width depth`, `MODEL id ../blocks/models/model.json x y z scale solid [yawDegrees]`, and `INTERACT id TYPE title message`. For `MODEL`, `solid=true` enables AABB collision for its volumetric cuboids; zero-thickness visual elements are not collidable. `PLANE` is always visual-only.
- **Entities:** continue using `data/entities/models/*.vmodel` for now. Entity skeleton/animation assets are a separate migration and should not be converted to Java Block/Item JSON.

## Example `.vmap`

```text
SKYVOXEL_MAP 1
MAP Example_Hub
SPAWN 0 1.2 8 0
PLACE central_plaza ../structures/plaza.vstruct 0 1.18 0 0
```

## Example `.vstruct`

```text
SKYVOXEL_STRUCTURE 1
NAME Small_Prop
CUBOID foundation stone 0 -0.25 0 2 0.5 2 true
MODEL lantern ../blocks/models/camping_lantern.json 0 0.5 0 1 false
```

Paths are relative to the containing file, and model textures are relative to their texture root. Keep `.vmap` as the layout, `.vstruct` as reusable placements, Blockbench `.json`/PNG as item and block visuals, and `.vmodel` only for entities and compatibility with old items.
