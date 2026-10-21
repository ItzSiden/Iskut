# Iskut Ocean World Data Update

This data update converts the currently edited hub from the former sky-world presentation to an ocean-world presentation.

## What changed

- `maps/main_hub.vmap` is now named `Iskut_Ocean_Hub`.
- A large, non-collidable `water` surface is placed below the island at sea level (top Y = -1.55).
- The `isle_west`, `isle_east`, `horizon_far` and `cloud_south` placements have been removed from the active map.
- The edited hub structures, spawn point and district placements are otherwise preserved.
- The unused old `skyland_*.vstruct` ghost-island/cloud assets are excluded from this clean data bundle.

## Files

- `maps/main_hub.vmap`: active map layout and structure placements.
- `structures/ocean_surface.vstruct`: ocean plane.
- `textures/world/water.png`: existing shared water material, reused by the ocean plane and existing fountains/ponds.

## Apply to the game

Use the companion `Iskut-Ocean-World-Patch.zip` installer, or replace the project's `data` directory with the contents of `Iskut-Ocean-World-Data.zip` after backing up your existing data folder.

The installer backs up the previous map, water plane if any, and obsolete skyland structure files before replacing/removing them. No Java source changes are required.

## Sea level

The water surface is centered at Y=-1.65 with height 0.2, so its top is Y=-1.55. The island top remains at Y=0. This submerges the lower foundation layers and leaves the upper terrain, buildings, spawn point and main landmarks untouched.
