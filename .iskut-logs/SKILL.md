---
name: iskut-asset-creation
description: "Create and validate Iskut assets: Blockbench Java Block/Item JSON plus PNGs for items and block props, entity models in .vmodel, .vmap world layouts, and placeable .vstruct structures. Use for hubs, dungeons, towns, arenas, and gameplay assets."
argument-hint: "Describe the model or map you want, including style, scale, layout, landmarks, and gameplay needs."
---

# Iskut Game Asset Creation

Create complete, game-compatible Iskut assets. **Items and block props use Blockbench's Minecraft Java Block/Item JSON plus referenced PNG textures. Entities still use `.vmodel` for now.** `.vmap` is the complete world layout; `.vstruct` contains reusable placeable structures. A structure may use legacy `CUBOID` geometry, a visual-only `PLANE`, or a Blockbench `MODEL` reference.

## Workflow

1. Identify whether the request is for an item/block model, entity model, map or placeable structure. Reuse the current scale and visual style.
2. For item/block props, create or edit in Blockbench and export **Minecraft Java Block/Item JSON**. Put item models in `data/items/models/<item-id>.json`, item PNGs under `data/items/textures/`, block/prop models in `data/blocks/models/`, and their PNGs under `data/blocks/textures/`.
3. Keep entity models on the separate `.vmodel` path until the entity format is migrated. Do not convert entity files to Java Block/Item JSON because that format does not provide the current bone-animation workflow.
4. Use `.vmap` only for `SPAWN` and `PLACE` references. Put reusable placeable world content under `data/structures/*.vstruct` and use `CUBOID`, `PLANE`, `MODEL`, and `INTERACT` as appropriate.
5. Validate JSON, texture references, dimensions, per-face UVs, relative paths, unique IDs, collision flags and spawn clearance.
6. Return complete output paths and a concise description. Do not overwrite user assets unless asked.

## File format

Use this exact structure:

```text
# Iskut entity model v1. Coordinates are local to the feet origin; rotation is degrees.
SKYVOXEL_ENTITY_MODEL 1
NAME model_id
HEIGHT 1.8
PART part_id material offsetX offsetY offsetZ width height depth rotationYDegrees
```

- `SKYVOXEL_ENTITY_MODEL 1` is required, exactly once.
- `NAME` is a single-token model ID. Use letters, digits, underscores, or hyphens; prefer a stable, descriptive `snake_case` ID. Keep it distinct from existing model IDs.
- `HEIGHT` is one positive, finite number in game units. Set it to the intended approximate overall model height.
- Each `PART` has exactly nine fields after `PART`: unique part ID, material, center offsets (`X Y Z`), dimensions (`width height depth`), and yaw in degrees.
- Each part is a cuboid centered at its offsets relative to the entity's feet origin. A part's vertical bounds are `offsetY - height/2` through `offsetY + height/2`.
- Width, height, and depth must be positive finite numbers. Offsets and yaw may be negative. Use simple decimal values, preferably to two decimal places.
- Yaw rotates the part about the vertical Y axis. Use `0` for most parts and small, deliberate angles for angled fins, ears, or similar details.
- Blank lines and lines beginning with `#` are ignored. There is no inline-comment syntax; put comments on their own lines.
- Include at least one `PART`. Unknown directives and extra or missing fields make the file invalid.

## Coordinates and composition

- `Y = 0` is the entity's feet origin; positive Y points upward. Put standing geometry above the ground, with the lowest part normally reaching approximately Y = 0.
- `X = 0` is the centerline. Keep the body centered and mirror paired limbs or features across X unless the design calls for asymmetry. Existing humanoid examples put their left parts at negative X.
- `+Z` is the front: existing character faces and the wisp's eyes are placed on the positive-Z side. Put backs, packs, or tails toward negative Z when appropriate.
- Offsets are part centers, not bottom corners. For example, a leg of height `0.7` resting on the ground has a center Y near `0.35`.
- Keep the highest geometry near `HEIGHT`. The runtime calculates the nameplate anchor from the actual top of the parts, so a part extending far above the declared height still moves the nameplate.
- Use proportions appropriate to the requested creature. Existing humanoid models are roughly 1.8 units tall and about 0.6 units wide at the torso; these are references, not hard limits.
- Keep most parts large enough to read at gameplay distance. Avoid excessive part counts and very thin slivers; add small face or accessory details only when they improve recognition.
- Give every part a unique, descriptive ID using letters, digits, underscores, or hyphens (for example `arm_left`, `eye_right`, `backpack`). Unique IDs also keep the browser editor's part list and selection reliable.

## Supported materials

Material names are case-insensitive in the game loader; use these lowercase names:

`navy`, `blue`, `teal`, `skin`, `dark`, `gold`, `violet`, `cyan`, `white`, `red`, `leaf`, `brown`

Choose only from this palette. Do not invent a color name or use a hex code: the loader rejects unknown materials. Use a small, cohesive subset of the palette, reserving brighter colors for important accents.

## Example

```text
# Iskut entity model v1. Coordinates are local to the feet origin; rotation is degrees.
SKYVOXEL_ENTITY_MODEL 1
NAME sample_scout
HEIGHT 1.8
PART leg_left navy -0.15 0.36 0 0.24 0.72 0.3 0
PART leg_right navy 0.15 0.36 0 0.24 0.72 0.3 0
PART torso blue 0 1.08 0 0.58 0.62 0.34 0
PART head skin 0 1.59 0 0.4 0.4 0.4 0
PART scarf gold 0 1.3 0.2 0.46 0.12 0.08 0
```

## Validation and delivery

Before returning the model:

- Confirm the header is exactly `SKYVOXEL_ENTITY_MODEL 1`.
- Confirm there is exactly one single-token `NAME`, one positive finite `HEIGHT`, and at least one valid `PART`.
- Confirm each `PART` has ten whitespace-separated tokens total, uses a supported material, has finite numeric values, positive dimensions, and a unique ID.
- Check that the feet, body, head, and any distinctive features form a coherent, front-facing silhouette and that the model's highest part is consistent with `HEIGHT`.
- Do not claim the model was tested in-game unless it was actually loaded by the game. The browser editor at `tools/model-editor/` can import and export `.vmodel` files for visual inspection.
- The game currently loads specific model paths in its client setup; adding a `.vmodel` file alone does not automatically make it appear in-game. If the user asks to use the model in gameplay, inspect the current entity/model registration code and make only the requested integration changes.

When saving an entity file, use a matching lowercase filename such as `sample_scout.vmodel`. Blockbench JSON files must use `.json` and PNG textures must exist at the referenced relative path. Do not place Markdown fences or prose inside asset files.

## `.vmap` map format

A `.vmap` defines a map name, the player spawn, and placements of reusable `.vstruct` files. It does not contain cuboid geometry directly.

```text
# Iskut handcrafted map v1
SKYVOXEL_MAP 1
MAP Skyland_Example
SPAWN 0 0 8 0
PLACE central_plaza ../structures/plaza.vstruct 0 0 0 0
```

- `SKYVOXEL_MAP 1` is required exactly once. `MAP` takes one token; underscores display as spaces.
- `SPAWN` takes finite world `x y z` coordinates and yaw in degrees. Position is the player's feet; choose a safe spot above a walkable surface and outside solid geometry.
- Each `PLACE` takes a unique instance ID, a structure path, world offset `x y z`, and placement yaw in degrees.
- Paths are relative to the `.vmap` file. For maps in `data/maps/`, structures in `data/structures/` are normally referenced as `../structures/name.vstruct`.
- A structure placement translates and rotates the structure's local positions around the vertical Y axis.
- The map must have at least one valid `PLACE`. IDs must use letters, digits, underscores, or hyphens and be unique among placements.
- Blank lines, full-line `#` comments, and inline comments preceded by whitespace and `#` are accepted.
- Unknown directives, unsupported versions, malformed numbers, missing files, or invalid structure references prevent loading.

## `.vstruct` reusable geometry format

Use `.vstruct` files to define geometry placed by a map. Positions refer to cuboid centers in structure-local coordinates.

```text
# Iskut reusable structure v1
SKYVOXEL_STRUCTURE 1
NAME Example_Plaza
CUBOID floor stone_dark 0 -0.25 0 16 0.5 16 true
CUBOID path stone_light 0 0.02 5 3 0.04 8 false
CUBOID shard crystal 2 0.6 1 0.35 0.6 0.35 false
INTERACT shard COLLECT_CRYSTAL Sky_Shard A_fragment_of_the_sky
```

- `SKYVOXEL_STRUCTURE 1` is required exactly once. `NAME` takes one token; underscores display as spaces.
- `CUBOID` syntax: `CUBOID id material x y z width height depth true|false [localYawDegrees]`. Dimensions must be positive finite numbers. Optional yaw is in degrees.
- `PLANE` syntax: `PLANE id material x y z width depth`; it is a top-facing, zero-thickness visual surface and never collides.
- `MODEL` syntax: `MODEL id ../blocks/models/model.json x y z scale true|false [localYawDegrees]`. It loads a Blockbench Java Block/Item JSON file and its PNGs. When `true`, only non-zero-volume cuboid elements become collision boxes; flat decorative elements remain visual-only.
- `solid=true` blocks movement and targeting. Use it for floors, walls, steps, and solid placed props. Use `solid=false` for decoration or objects intended not to block movement. Item models are always visual-only.
- Cuboid IDs must be unique in their structure and contain only letters, digits, underscores, or hyphens.
- `INTERACT` syntax: `INTERACT id TYPE title_token message_token`. It must reference an existing cuboid, with at most one interaction per cuboid. Supported types: `COLLECT_CRYSTAL`, `PORTAL`, `NPC`, `SHOP`, and `INFO`.
- Use underscores instead of spaces in interaction title and message tokens; the game displays underscores as spaces.
- Each structure must contain at least one `CUBOID`, `PLANE`, or `MODEL`. Interactions are optional.
- Supported structure materials: `stone`, `stone_dark`, `stone_light`, `slate`, `grass`, `moss`, `wood`, `wood_light`, `brick`, `roof_blue`, `roof_red`, `gold`, `crystal`, `cyan`, `violet`, `water`, `leaves`, `sand`, `white`.
- Do not put geometry directives in `.vmap`; place model geometry through a `.vstruct` `MODEL` directive.

## Map layout and playability

- Leave open space around the spawn and orient the player toward the main route or landmark.
- Connect paths to destinations; keep gates, bridges, stairs, and entrances clear and traversable.
- Account for cuboid centers. A floor with its top surface at `y = 0` and thickness `0.4` has center `y = -0.2`.
- Avoid collidable overlaps that can trap the player. Use `solid=false` for decoration resting over solid floors.
- Reuse existing structure files when suitable. Use distinct placement IDs for repeated instances.
- Check final world-space positions after placement translation and yaw; locally valid structures can overlap when placed together.
- The current hub's plazas and routes span tens of units; use it as a scale reference, not a requirement.

Example map at `data/maps/example_hub.vmap`:

```text
# Iskut handcrafted map v1
SKYVOXEL_MAP 1
MAP Example_Hub
SPAWN 0 0 8 0
PLACE plaza ../structures/example_plaza.vstruct 0 0 0 0
```

Matching `data/structures/example_plaza.vstruct`:

```text
# Iskut reusable structure v1
SKYVOXEL_STRUCTURE 1
NAME Example_Plaza
CUBOID floor stone_dark 0 -0.25 0 20 0.5 20 true
CUBOID inlay stone_light 0 0.02 0 4 0.04 4 false
```

## Validation and delivery

For `.vmodel`, confirm the header is exactly `SKYVOXEL_ENTITY_MODEL 1`, there is a single-token `NAME`, a positive finite `HEIGHT`, and at least one `PART`. Each `PART` must have exactly nine fields after its directive, a unique ID, supported material, finite numbers, and positive dimensions. Check the silhouette faces `+Z`, the feet meet approximately `Y = 0`, and the highest part agrees with `HEIGHT`.

For `.vmap` and `.vstruct`, confirm the version headers, required directives, numeric values, positive dimensions, supported materials, valid unique IDs, safe relative paths, and interaction references. Check spawn clearance, routes, collision choices, and transformed placement positions. A map must place at least one structure; a structure must contain at least one `CUBOID`, `PLANE`, or `MODEL` entry.

The browser editor at `tools/model-editor/` is a legacy editor for `.vmodel` and `.vstruct`; use Blockbench for new item/block JSON assets. Do not claim assets were tested in-game unless they were actually loaded. Adding a model file alone does not register it in gameplay. The default map is `data/maps/main_hub.vmap`; load another map explicitly with `--map=data/maps/<file>.vmap`. The map loader places reusable `.vstruct` files; `.vmap` does not contain raw geometry. Procedural terrain is not part of the normal map pipeline.
