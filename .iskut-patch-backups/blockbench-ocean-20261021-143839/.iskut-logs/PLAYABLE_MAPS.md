# Iskut hand-authored maps (v1)

The normal game launch now loads `data/maps/main_hub.vmap`. The generated terrain scene is a render test only and is loaded only when `--voxel` is explicitly supplied.

## Run the actual hub

```bat
run-client.bat
```

Optional renderer tuning still works:

```bat
run-client.bat --view=48 --threads=2 --render-scale=0.95
```

To run the old generated-terrain benchmark, explicitly use `--voxel`. To run the debug cuboids, use `--demo`. To load a different map file:

```bat
run-client.bat --map=data/maps/main_hub.vmap
```

## Map source files

- `data/maps/*.vmap` places reusable structure files and defines the spawn point.
- `data/structures/*.vstruct` defines static cuboid geometry and object interactions.
- All locations and sizes are world units. Every `CUBOID` size is independent, so it can be a long beam, flat slab or tall pillar; it is not constrained to a cube grid.
- Object positions describe the cuboid center.
- `solid=true` enables player collision and ray targeting; `solid=false` is decorative geometry.

### `.vmap` directives

```text
SKYVOXEL_MAP 1
MAP Skyland_Hub
SPAWN x y z yawDegrees
PLACE instanceId ../structures/plaza.vstruct x y z yawDegrees
```

### `.vstruct` directives

```text
SKYVOXEL_STRUCTURE 1
NAME Central_Plaza
CUBOID id material x y z width height depth true|false [localYawDegrees]
INTERACT id COLLECT_CRYSTAL|PORTAL|NPC|SHOP|INFO title_token message_token
```

Text tokens currently use underscores instead of spaces. IDs must be unique within a structure and should remain stable for future editor exports. Supported material names are defined in `MapLoader` (for example `stone`, `stone_dark`, `slate`, `grass`, `wood`, `gold`, `crystal`, `cyan`, `violet`, `water`, and `leaves`).

The loader validates file versions, numbers, material names, duplicate IDs and interaction references before returning a scene.

## Current playable slice

- A hand-authored, static Skyland hub built from 115 variable-sized cuboid objects.
- A central plaza, portal gate, merchant stalls, simple blocky tree planters, lamps, a fountain and two collectible Sky Shards.
- Look at an interactable object; when `[E] ...` appears, press E. Sky Shards are removed from the current scene and counted in the HUD. The portal, shop and archivist currently provide messages.
- The map is client-side and local for now. Dedicated server authority, persistent inventory, animated NPCs, actual shop menus, portal travel and the custom GUI editor are future milestones.
