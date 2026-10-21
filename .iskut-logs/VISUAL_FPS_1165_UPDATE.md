# Iskut Visual + FPS Update

This update moves Iskut's hand-authored Skyland hub closer to the textured, smoothly lit feel of a modern voxel game (Minecraft 1.16-era as a visual reference), while keeping the renderer pure Java and friendly to older CPUs/Intel HD hardware. Iskut keeps its own palette and layouts; this is not a Minecraft clone.

## Visual changes

- Added a quieter irregular cobblestone tile for the enormous plaza foundation. The older repetitive horizontal-brick texture was making the whole floor read like a 1990s tiled platform.
- World materials now include 20 shared 16x16 pixel-art PNGs, with the current hub using 19: cobble, stone variants, slate, grass/moss, wood, brick, roof tiles, leaves, sand, water, metal/gem accents and white cloud material.
- UV density stays at one texture tile per world unit. Textures use more intentional stone clusters, masonry seams, roof shingles, wood grain and lower-contrast details.
- The software renderer builds compact mipmaps for every texture and chooses a mip level per triangle from perspective-correct UV gradients. This reduces distance shimmer/moire while retaining close-up pixel-art texture detail.
- Cuboid faces receive warm/cool directional face shading plus interpolated vertex gradients. Top surfaces are subdivided to a 2.5-world-unit grid for baked shading; sides and hidden bottom faces use a cheaper 4-world-unit grid.
- At map load, static top vertices get a low-cost proximity-based contact-occlusion halo and a short directional sun-shadow test using world cuboid bounds. This approximates soft block ambient occlusion and small static shadows without a real-time shadow map or per-frame shadow pass.
- Static map geometry is merged by shared material and 48-unit XZ chunk into a render-only scene. The original authored scene is retained for collision, interactions, entity targeting and map edits. Interactive objects remain individually addressable.
- The hub's horizon now has additional handcrafted floating islands, tree silhouettes and puffy cloud clusters positioned to be visible from player camera height.
- `run-hub.bat` launches Smooth quality with 2 worker threads, 90% internal render scale and 64-unit view distance.

## FPS/performance notes

- Current authored hub: 164 objects and interaction points in the original scene; 53 render batches/interactable draws in the render scene.
- At 640x360, the current world test submits 6,666 triangles, down from 12,632 with the earlier denser all-face subdivision. The world preview benchmark measured about 5.0 ms/frame in this development environment with the Smooth software renderer at one thread. This is a relative dev-host benchmark, **not** a promise of FPS on every user's machine.
- Render batches reduce submission/transform overhead, while adaptive subdivision avoids spending extra triangles on hidden bottoms and vertical faces. Actual FPS depends on CPU, output size/render scale, scene visibility and background load.
- There are no real-time shadow maps, global illumination, normal maps or PBR materials. The static contact/sun shading is baked when the map is loaded and should be rebuilt if a map is edited at runtime.

## Compatibility

- `.vmap` and `.vstruct` remain version 1 and use their existing material IDs. The only additional world material ID used by the map is `cobble`; old materials remain available.
- `GameMap.scene()` remains the original authoritative collision/interaction scene. `GameMap.renderScene()` is a render-only batch scene.
- Existing `.vmodel`, inventory, save/load, movement, combat and entity hologram pipelines are unchanged.

## Verification

Run from the project root (Java 21). First generate a current source list, then compile:

```powershell
New-Item -ItemType Directory -Force .build-check/classes | Out-Null
Get-ChildItem render-api/src/main/java,engine-core/src/main/java,ui-core/src/main/java,renderer-software/src/main/java,game-client/src/main/java,tools/dev -Filter *.java -Recurse | ForEach-Object FullName | Set-Content .build-check/sources.txt
javac --release 21 -d .build-check/classes @.build-check/sources.txt
java -cp .build-check/classes dev.itzsiden.skyvoxel.client.StaticWorldBatchingSmokeTest
java -cp .build-check/classes dev.itzsiden.skyvoxel.client.WorldLightingSmokeTest
java -cp .build-check/classes dev.itzsiden.skyvoxel.client.WorldTexturingSmokeTest
java -cp .build-check/classes dev.itzsiden.skyvoxel.client.WorldVisualPerformanceTest
java -cp .build-check/classes dev.itzsiden.skyvoxel.client.WorldPlayerViewPreviewTest
```

Also run `PlayerPhysicsSmokeTest`, `GameplayFoundationSmokeTest`, `InventorySystemSmokeTest`, `IskutUiNameplateSmokeTest`, `HeldItemPoseSmokeTest`, `TexturedVModelSmokeTest`, and `ItemVisualPipelineSmokeTest` for regressions. On a headless test host, add `-Djava.awt.headless=true` to GUI raster smoke-test invocations.
