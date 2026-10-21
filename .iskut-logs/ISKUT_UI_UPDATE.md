# Iskut UI and Nameplate Update

## What changed
- Entity nameplates use the top of actual authored model geometry as a world-space anchor.
- Each frame projects that anchor through the current view-projection matrix; the previous 20 Hz screen-position cache has been removed to eliminate camera-turn lag.
- Nameplates use a compact translucent black plate, neutral white name, subtle type tint and a thin health bar without glowing borders.
- The default HUD is hidden of developer telemetry. `F3` toggles diagnostics. The health/mana panel is compact and shows resource bars plus DEF/STR/INT.
- UI text uses Inter when the external font file exists.

## Font file to add
Download Inter's regular TTF font and place it at:

`data/fonts/Inter.ttf`

The game accepts a normal `.ttf` or `.otf` file. Do not rename `.obb`, `.woff` or `.woff2` files to `.ttf`; that will not convert them. Until Inter is placed there, Iskut keeps working with the system SansSerif fallback. No font is bundled in this patch.

## Install
Run `PATCH_ISKUT_UI_NAMEPLATES.bat` extracted next to your project, or use the full project ZIP. The installer saves replaced files under `.iskut-backups\ui-nameplates-<timestamp>`.
