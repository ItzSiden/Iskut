# Iskut Visual Feel + Movement Physics Update

## Goals
This update softens the existing pixel-textured voxel look without expensive post-processing, and makes moving around small cuboid geometry feel less rigid. It keeps the software renderer as the low-end fallback.

## What changed

### Visual presentation
- The hub launcher defaults to `--quality=smooth --render-scale=0.90`.
- The final world framebuffer is bilinearly upscaled by the window presenter. UI text remains at window resolution; material textures themselves keep nearest-filtered pixel art.
- Default cuboid and item-model face lighting is less harsh. Per-vertex corner shading adds an inexpensive ambient-occlusion-like gradient at lower/corner vertices.
- Fog is now interpolated from depth per pixel in scanline runs rather than assigned once per triangle, so visible distance transitions have fewer hard polygon-shaped bands.
- Smooth mode still uses deterministic software rasterization. `--quality=fast --render-scale=0.95` restores a crisper look and prioritizes speed.

### Movement and collision
- Grounded movement attempts an automatic step when horizontal motion hits a floor-connected cuboid.
- The maximum automatic step height is 1.0 world unit.
- A successful step is accepted only if the player AABB fits at the new height; a ceiling or other obstruction prevents it.
- Tall walls and floating blocks are not auto-climbed.
- Step and landing camera offsets are damped to reduce abrupt vertical snaps.
- Existing gravity, jump buffering/coyote-time, sprint, crouch, fall damage and AABB collision remain in place.

## Current physics limits
Collision is still AABB-based against static cuboids/entities. This update adds stair-like stepping across small cuboids, not arbitrary triangle-mesh slope collision, capsule collision, moving platforms, or full rigid-body physics. A future continuous-ramp feature should introduce explicit collision shapes rather than inferring physics from visual meshes.

## Performance / verification
The following passed using Java 21 in a headless test run:
- `PlayerPhysicsSmokeTest`: 0.2, 0.5 and 1.0 unit steps are climbed and traversed; 1.4 unit wall blocks; low-ceiling step is rejected.
- `WorldLightingSmokeTest`: 115 hub objects, 690 gradient faces, 0 flat faces, light range 0.549..1.000.
- Item visual, textured world, inventory/persistence, gameplay/combat, held-item pose and hologram regressions.
- Smooth renderer output is pixel-identical with one versus four worker threads.

Offscreen benchmark on the build environment at 640x360:
- FAST, view 48: 2.67 ms/frame; view 96: 4.17 ms/frame.
- SMOOTH, view 48: 3.36 ms/frame; view 96: 5.21 ms/frame.
These are only environment-specific relative measurements, not expected FPS guarantees on the player's PC.

The OpenGL backend wasn't directly compiled in the offline verification environment because LWJGL dependencies were unavailable there. The Windows installer asks Gradle to compile it alongside the other modules.
