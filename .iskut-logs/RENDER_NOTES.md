# Iskut render engine v2 + hand-authored gameplay map

## Run the actual game hub (default)

```bat
run-client.bat
```

Recommended low-end software-renderer settings, matching the current user's good performance result:

```bat
run-hub.bat
```

Equivalent command:

```bat
run-client.bat --view=48 --threads=2 --render-scale=0.95
```

The default startup reads the hand-authored map at `data/maps/main_hub.vmap`. No procedural terrain is generated for normal gameplay.

## Renderer/debug demos (opt-in only)

```bat
run-client.bat --voxel                      rem procedural terrain benchmark demo only
run-client.bat --demo                       rem simple variable-sized cuboid scene
run-client.bat --benchmark=500              rem benchmark cuboids
run-client.bat --view=48 --render-scale=0.5 --threads=2
```

Flags: `--view=16..240` (draw/fog distance, default 64), `--quality=fast|smooth`, `--threads=N`,
`--render-scale=0.25..1.0`, `--map=data/maps/main_hub.vmap`, `--no-culling`.

## Hand-authored content

- `data/maps/*.vmap` composes a map from reusable structures and defines spawn.
- `data/structures/*.vstruct` defines variable-sized cuboids and interaction points.
- `PLAYABLE_MAPS.md` documents the file syntax.
- Current main hub contains 115 authored objects and 7 interaction bindings: two collectible Sky Shards, a portal, shop and archivist surfaces/NPC stand-ins.

## Renderer architecture

`render-api` remains backend independent; `renderer-software` and `renderer-opengl` implement that API. The software renderer uses scanline spans, a depth buffer, perspective-correct UV interpolation, fog and tile/band rasterization to keep CPU work reasonable. The full source archive includes renderer changes from the user's uploaded `Iskut-render-v2.zip`.
