# Blockbench Java Block/Item models for Iskut

Export a **Minecraft Java Block/Item** model from Blockbench as `.json` into this folder. Put its referenced PNG textures under `data/blocks/textures/` using the same relative path the model specifies. For convenience, a texture name such as `stone` also falls back to `data/textures/world/stone.png`.

A `.vstruct` places a Java model with:

```text
MODEL model_id ../blocks/models/stone_block.json x y z scale solid [localYawDegrees]
```

`x y z` is the model origin (the model's [8, 8, 8] center maps to the placement point), `scale` is a world multiplier, and `solid` enables per-element AABB collision for that placed structure model. Use `false` for decoration-only models. **Do not use structure collision for inventory item models**: item models load from `data/items/models/` and remain visual-only.

`.vmap` remains the full world layout, containing `PLACE` lines for reusable `.vstruct` files under `data/structures/`. `.vstruct` is the reusable structure placement file. Existing `CUBOID` entries remain supported for legacy terrain/large tiled surfaces; new props and detailed blocks can use `MODEL` entries to reference Blockbench exports.
