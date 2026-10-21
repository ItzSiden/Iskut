# Iskut `.vmodel` v2: Editor Texture Tool Integration Spec

**Audience:** the Three.js HTML/CSS/JavaScript map/model editor. This note describes the model format and editor work required for a Blockbench-style pixel-texturing workflow.

## Goal

An artist authors exactly one self-contained `<model-id>.vmodel`. The file contains cuboid geometry, per-face pixel texture selection/orientation, and the actual pixel colors. Iskut generates the inventory thumbnail, first-person held viewmodel and world-drop visual automatically. Do not export or require a separate PNG icon or hand model per item.

Version 1 flat-colour models remain supported. New editor exports should use version 2.

## File grammar

UTF-8, one directive per line. Blank lines and lines whose first non-space character is `#` are comments. IDs use simple no-whitespace names; use lowercase snake_case for stable IDs.

### Header and parts

```text
ISKUT_ITEM_MODEL 2
NAME training_blade
HEIGHT 0.92

PART grip leather 0.00 0.035 0.00 0.105 0.27 0.105 -8
PART blade iron 0.00 0.515 0.00 0.145 0.59 0.055 0
FACE blade FRONT blade_engraving 0 0 0
FACE blade BACK blade_engraving 180 1 0
```

Equivalent headers accepted by the loader are `ISKUT_MODEL 2`, `SKYVOXEL_ITEM_MODEL 2`, and `SKYVOXEL_ENTITY_MODEL 2`. The latter lets entity models use the same format.

`PART <part-id> <default-material-or-texture-id> <x> <y> <z> <width> <height> <depth> <rotationY-degrees>` describes a cuboid. Position is the part's centre relative to the model origin. Dimensions must be positive. Rotation is around local Y, in degrees. Item weapons should put the origin at the grip; potion/material origins are usually near their centre. For entity models, origin is at the feet as in v1.

The part's material token is also its default face texture ID if a `TEXTURE` with that ID exists. Otherwise it becomes a flat-colour fallback using the v1 named material/RGB palette. An explicit `FACE` directive overrides a single face's default mapping.

### Embedded pixel textures

A texture block stores a palette plus indexed pixel rows. No external image file is required.

```text
TEXTURE blade_engraving 8 8
PALETTE blade_engraving 0 #1A2838
PALETTE blade_engraving 1 #7893A9
PALETTE blade_engraving 2 #C7D8E4
PALETTE blade_engraving 3 #2CD6D0
PALETTE blade_engraving . #00000000
PIXEL blade_engraving 0 ...001..
PIXEL blade_engraving 1 ..01210.
PIXEL blade_engraving 2 .0123210
PIXEL blade_engraving 3 12333321
PIXEL blade_engraving 4 .0123210
PIXEL blade_engraving 5 ..01210.
PIXEL blade_engraving 6 ...030..
PIXEL blade_engraving 7 ....0...
END_TEXTURE
```

Rules:

- `TEXTURE <id> <width> <height>` dimensions must be powers of two, from 1×1 through 128×128.
- `PALETTE <texture-id> <symbol> <color>` defines one visible character. The symbol must be one non-whitespace character. Each symbol is declared once per texture.
- Colors accept `#RRGGBB` (opaque) or `#RRGGBBAA` (alpha is the final byte; `#00000000` is transparent).
- `PIXEL <texture-id> <row-index> <symbols>` uses a **zero-based** row index. Each row must contain exactly `<width>` symbols, and each symbol must exist in the palette. Row `0` is the top row of the image.
- Every row must appear exactly once and the block must end with `END_TEXTURE`.
- Use `.` as a conventional transparent palette symbol. The renderer uses alpha cutout for model textures; alpha below 128 is discarded during 3D drawing.
- Current model atlas limit is 2048×2048, and a model's texture sources are packed into one nearest-filtered atlas at load time.

### Face mappings

```text
FACE <part-id> <TOP|BOTTOM|FRONT|BACK|LEFT|RIGHT> <texture-id> [rotation-degrees] [mirrorU] [mirrorV]
```

- `TOP` is +Y, `BOTTOM` is -Y, `FRONT` is +Z, `BACK` is -Z, `RIGHT` is +X, `LEFT` is -X.
- Rotation is optional and must be `0`, `90`, `180`, or `270`. Rotation is clockwise when viewing that face from outside.
- `mirrorU` and `mirrorV` are optional `0`/`1` flags applied before rotation.
- The named texture must be declared in the same model file; the `PART` must be defined before its `FACE` directives.
- If no `FACE` entry exists, that face uses the part's default texture/material. All six faces inherit this default independently.
- A face maps the selected pixel image across the full face. Current format intentionally has no arbitrary triangle UVs; cuboid-face UVs are the supported authoring surface.

## Editor UI requirements

Add a **Textures** workspace to the existing Three.js editor:

1. **Part/cuboid tree:** select a named part; show its dimensions, pivot/offset, and Y rotation.
2. **Six-face picker:** Top, Bottom, Front, Back, Left, Right. Show each face's texture thumbnail, rotation, U mirror, and V mirror.
3. **Pixel canvas:** each texture owns a power-of-two pixel canvas (start with 16×16 default, allow 8/16/32/64/128). Provide pencil, erase/transparent, fill, eyedropper, line/rectangle, palette swatches, undo/redo, zoom, and nearest-neighbour display. Do not CSS-scale the canvas with smoothing.
4. **Shared palette:** editing a palette swatch updates every matching indexed pixel in that texture. Preserve alpha; transparent color should be visible as a checkerboard in the editor only.
5. **Live 3D preview:** apply the currently selected face UVs/textures to the Three.js cuboid preview using nearest filtering. The preview and exported geometry must use the same local axes and face naming above.
6. **Export:** write one self-contained `.vmodel` v2 with texture blocks, `PART` directives and only the needed `FACE` overrides. Keep row indices zero-based and export all rows. Do not output separate icon PNGs.
7. **Import:** support v1 by presenting each flat part material as its default flat texture/material; allow it to be painted and export it as v2. Preserve part IDs, dimensions, offsets, rotations and model height.
8. **Validation:** before export, reject duplicate texture/part IDs, missing palettes/rows, wrong row widths, undefined palette symbols, invalid face names, invalid transforms, missing face texture IDs, non-power-of-two texture sizes, and texture dimensions over 128. Report the model ID and line/part/face in errors where possible.
9. **Filename contract:** `data/items/models/<item-id>.vmodel` must match the `.item` ID exactly. Entity model paths may use other IDs, but reuse the same v2 grammar.

## Runtime behaviour

- Model files are parsed once during startup, not per frame.
- Texture blocks are packed into an internal atlas with a small padded border to prevent adjacent tile sampling.
- Item icon generation samples the model's textured faces and caches the transparent PNG at `data/cache/item-icons/<item-id>.png`.
- The cache is disposable. A content fingerprint plus icon-generator version invalidates stale icons whenever the `.vmodel` changes.
- The same model `Mesh` and `Material` instances are used by the inventory-icon path, held-item scene, and world-drop scene.
- Keep the face labels and UV orientation identical in Three.js and Java. An easy conformance test is a 4×4 texture with a distinct color in each corner, assigned to one cuboid face; rotation/mirror should look identical in editor preview, generated icon and held/world render.

## Example validation checklist

- [ ] Import a v1 model, preserve shape, then export v2.
- [ ] Paint different patterns on all six faces of one cuboid.
- [ ] Rotate a face texture by 90° and mirror U/V independently.
- [ ] Use a transparent pixel around a gem/rune silhouette.
- [ ] Edit the model after first launch and confirm the item thumbnail regenerates.
- [ ] Compare the same item in inventory, first-person hand, and a world-drop preview.
- [ ] Verify the output is still a single `.vmodel` file.
