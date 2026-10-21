# Iskut World Textures and Item Viewmodel (Current)

## Current model asset pipeline

- Item models are Blockbench Minecraft Java Block/Item JSON at `data/items/models/<item-id>.json` with PNGs under `data/items/textures/`.
- Block and prop models are Blockbench Java Block/Item JSON at `data/blocks/models/` with PNGs under `data/blocks/textures/`; `.vstruct` `MODEL` entries place them in the world.
- Entity models remain on the separate `.vmodel` pipeline for now.
- The one item model asset supplies generated thumbnails, held viewmodels and dropped-item visuals. Items are non-collidable. Structure collision is enabled per `.vstruct` `MODEL` placement and ignores zero-thickness decorative elements.
- Shared world material textures remain in `data/textures/world/` and are referenced by material IDs from legacy `.vstruct` `CUBOID` entries.

## Held model conventions

For held weapons/tools, name the intended grip part `grip`, `handle`, `hilt`, or `haft`: the camera-relative viewmodel anchors that part inside Iskut's custom Arc-Guard hand. If no semantic grip exists, the viewmodel uses a generic origin-based placement.

## World textures

World structures use shared textures under `data/textures/world/`. Current IDs include `stone`, `stone_dark`, `stone_light`, `slate`, `dirt`, `cobble`, `grass`, `moss`, `wood`, `wood_light`, `brick`, `roof_blue`, `roof_red`, `gold`, `crystal`, `cyan`, `violet`, `water`, `leaves`, `sand`, `wet_sand`, `shore_transition`, `shallow_water`, `foam`, `white`, `portal`, and wool variants. New structures can keep using these IDs without copying the PNG into each `.vstruct`.

## Testing

`tools/dev/itzsiden/skyvoxel/client/BlockbenchModelSmokeTest.java` checks starter item JSON import, generated thumbnails, non-square PNG input, zero-thickness visual elements and structure model placement. `OceanWorldSmokeTest.java` verifies the Y=1 ocean plane, non-collision and separation from static-world AO/coplanar processing.
