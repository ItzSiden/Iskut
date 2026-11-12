from pathlib import Path
import zipfile, hashlib, textwrap, os

root = Path("C:/Users/Hello/Desktop/Iskut/test/data/Iskut_Ship_Exploration_Patch")
patch = root / "patch"
files = {}

files["game-client/src/main/java/dev/itzsiden/skyvoxel/client/IslandArchipelago.java"] = r'''package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Transform;

/**
 * Lightweight handcrafted sailing destinations for the hub ocean.
 * Each island is added to the collision scene and the render scene.
 */
final class IslandArchipelago {
    record Landing(float x, float surfaceY, float z) { }

    private record Island(String id, float x, float z, float width, float depth, float topY) { }

    private static final Island[] ISLANDS = {
        new Island("mosslight_key", 0.0f, -150.0f, 42.0f, 38.0f, 4.5f),
        new Island("coral_reach", 145.0f, -245.0f, 36.0f, 42.0f, 4.8f),
        new Island("frostglass_isle", -155.0f, -350.0f, 38.0f, 34.0f, 5.2f)
    };

    private IslandArchipelago() { }

    static void populate(Scene world, Scene renderWorld) {
        Mesh cube = new Mesh("archipelago-shared-cuboid", CuboidMeshFactory.createUnitCuboid());
        Material cliff = new Material("archipelago_cliff", 0.27f, 0.34f, 0.39f);
        Material darkStone = new Material("archipelago_dark_stone", 0.18f, 0.24f, 0.29f);
        Material grass = new Material("archipelago_grass", 0.24f, 0.52f, 0.29f);
        Material moss = new Material("archipelago_moss", 0.14f, 0.39f, 0.24f);
        Material sand = new Material("archipelago_sand", 0.72f, 0.62f, 0.42f);
        Material wood = new Material("archipelago_wood", 0.38f, 0.24f, 0.15f);
        Material leaves = new Material("archipelago_leaves", 0.14f, 0.43f, 0.24f);
        Material rock = new Material("archipelago_rock", 0.45f, 0.49f, 0.52f);

        for (Island island : ISLANDS) {
            String id = island.id();
            float foundationHeight = island.topY() + 1.0f;
            float foundationY = (island.topY() - 1.0f) * 0.5f;

            block(world, renderWorld, cube, id + "/foundation", cliff,
                island.x(), foundationY, island.z(), island.width(), foundationHeight, island.depth());
            block(world, renderWorld, cube, id + "/lower-rim", darkStone,
                island.x(), island.topY() - 0.72f, island.z() + 1.0f,
                island.width() + 1.4f, 0.55f, island.depth() - 1.0f);
            block(world, renderWorld, cube, id + "/shoreline", sand,
                island.x(), island.topY() - 0.42f, island.z(),
                island.width() - 1.4f, 0.28f, island.depth() - 1.4f);
            block(world, renderWorld, cube, id + "/grass-top", grass,
                island.x(), island.topY() - 0.14f, island.z(),
                island.width() - 2.2f, 0.28f, island.depth() - 2.2f);

            // Offset raised patches break the otherwise rectangular silhouette.
            block(world, renderWorld, cube, id + "/west-shelf", moss,
                island.x() - island.width() * 0.34f, island.topY() - 0.05f,
                island.z() + island.depth() * 0.22f, island.width() * 0.28f, 0.18f, island.depth() * 0.30f);
            block(world, renderWorld, cube, id + "/east-shelf", grass,
                island.x() + island.width() * 0.32f, island.topY() - 0.06f,
                island.z() - island.depth() * 0.24f, island.width() * 0.25f, 0.16f, island.depth() * 0.28f);

            // Small, low-poly landmarks make the destinations readable from the water.
            block(world, renderWorld, cube, id + "/marker-stone", rock,
                island.x() - 5.0f, island.topY() + 0.70f, island.z() - 4.0f, 1.6f, 1.4f, 1.5f);
            block(world, renderWorld, cube, id + "/marker-cap", sand,
                island.x() - 5.0f, island.topY() + 1.48f, island.z() - 4.0f, 1.15f, 0.18f, 1.1f);

            // Two blocky palms per island.
            palm(world, renderWorld, cube, id + "/palm-a", wood, leaves,
                island.x() + 7.0f, island.topY(), island.z() + 5.0f);
            palm(world, renderWorld, cube, id + "/palm-b", wood, leaves,
                island.x() - 9.0f, island.topY(), island.z() - 6.0f);
        }
    }

    static Landing findLanding(float x, float z) {
        // Safe return point on the harbor pier.
        if (Math.abs(x) <= 12.0f && z >= 47.0f && z <= 70.0f) {
            return new Landing(clamp(x + 3.0f, -8.8f, 8.8f), 1.18f, clamp(z, 52.0f, 61.0f));
        }

        for (Island island : ISLANDS) {
            float halfW = island.width() * 0.5f - 4.0f;
            float halfD = island.depth() * 0.5f - 4.0f;
            if (Math.abs(x - island.x()) <= halfW && Math.abs(z - island.z()) <= halfD) {
                float landingX = clamp(x + 3.0f,
                    island.x() - island.width() * 0.5f + 2.5f,
                    island.x() + island.width() * 0.5f - 2.5f);
                float landingZ = clamp(z,
                    island.z() - island.depth() * 0.5f + 2.5f,
                    island.z() + island.depth() * 0.5f - 2.5f);
                return new Landing(landingX, island.topY(), landingZ);
            }
        }
        return null;
    }

    private static void palm(Scene world, Scene renderWorld, Mesh cube, String id,
                             Material wood, Material leaves, float x, float topY, float z) {
        block(world, renderWorld, cube, id + "/trunk", wood, x, topY + 1.05f, z, 0.48f, 2.1f, 0.48f);
        block(world, renderWorld, cube, id + "/crown", leaves, x, topY + 2.30f, z, 2.0f, 1.05f, 1.8f);
        block(world, renderWorld, cube, id + "/crown-top", leaves, x, topY + 2.92f, z, 1.25f, 0.45f, 1.2f);
    }

    private static void block(Scene world, Scene renderWorld, Mesh cube, String id, Material material,
                              float x, float y, float z, float width, float height, float depth) {
        RenderObject object = new RenderObject(id, cube, material,
            new Transform().setPosition(x, y, z).setScale(width, height, depth)).setCollidable(true);
        world.add(object);
        if (renderWorld != world) renderWorld.add(object);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
'''

files["game-client/src/main/java/dev/itzsiden/skyvoxel/client/ShipExplorationSystem.java"] = r'''package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseDelta;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;

/** Small, game-thread sailing controller for one player-owned harbor cutter. */
final class ShipExplorationSystem {
    private static final String VESSEL_ID = "player-harbor-cutter";
    private final EntityWorld entities;
    private final EntityModel model;

    private Entity vessel;
    private boolean boarded;
    private float x;
    private float y = 0.78f;
    private float z = 66.0f;
    private float yaw;
    private float pitch;
    private float speed;
    private float waveTime;

    ShipExplorationSystem(EntityWorld entities, EntityModel model) {
        this.entities = java.util.Objects.requireNonNull(entities, "entities");
        this.model = java.util.Objects.requireNonNull(model, "model");
    }

    boolean boarded() { return boarded; }
    float speed() { return speed; }

    void summonAndBoard(PlayerController player) {
        if (vessel == null) {
            vessel = new Entity(VESSEL_ID, EntityType.PET, model, "Harbor Cutter", "PLAYER VESSEL")
                .setShowNameplate(false).setShowHealthBar(false);
            entities.add(vessel);
        }
        x = 0.0f;
        y = 0.78f;
        z = 66.0f;
        yaw = 0.0f;
        pitch = 0.0f;
        speed = 0.0f;
        waveTime = 0.0f;
        boarded = true;
        syncVessel();
        placePlayerAtHelm(player);
    }

    boolean canBoard(PlayerController player) {
        if (boarded || vessel == null || !vessel.visible()) return false;
        double dx = player.x() - x;
        double dz = player.z() - z;
        return dx * dx + dz * dz <= 30.25;
    }

    void board(PlayerController player) {
        if (vessel == null || boarded) return;
        boarded = true;
        speed = 0.0f;
        yaw = vessel.yaw();
        x = vessel.x();
        y = vessel.y();
        z = vessel.z();
        placePlayerAtHelm(player);
    }

    boolean disembark(PlayerController player) {
        if (!boarded) return false;
        IslandArchipelago.Landing landing = IslandArchipelago.findLanding(x, z);
        if (landing == null) return false;

        boarded = false;
        speed = 0.0f;
        player.setExternalPose(landing.x(), landing.surfaceY(), landing.z(), yaw, 0.0);
        return true;
    }

    void update(RenderWindow window, float deltaSeconds, PlayerController player) {
        if (!boarded || vessel == null) return;

        float dt = Math.max(0.0f, Math.min(deltaSeconds, 0.05f));
        MouseDelta mouse = window.consumeMouseDelta();
        yaw += mouse.x() * 0.0022f;
        pitch = clamp(pitch - mouse.y() * 0.0020f, -0.95f, 0.95f);

        float throttle = (window.isKeyDown(Key.W) ? 1.0f : 0.0f)
            - (window.isKeyDown(Key.S) ? 1.0f : 0.0f);
        float steering = (window.isKeyDown(Key.D) ? 1.0f : 0.0f)
            - (window.isKeyDown(Key.A) ? 1.0f : 0.0f);

        if (throttle > 0.0f) speed = Math.min(8.0f, speed + 4.2f * dt);
        else if (throttle < 0.0f) speed = Math.max(-2.8f, speed - 4.0f * dt);
        else speed *= (float) Math.exp(-0.78f * dt);

        if (Math.abs(speed) > 0.06f) {
            yaw += steering * 1.25f * dt * (speed >= 0.0f ? 1.0f : -1.0f);
        }

        x += (float) Math.sin(yaw) * speed * dt;
        z -= (float) Math.cos(yaw) * speed * dt;
        x = clamp(x, -1800.0f, 1800.0f);
        z = clamp(z, -1800.0f, 1800.0f);

        waveTime += dt;
        y = 0.78f + (float) Math.sin(waveTime * 1.65f) * 0.035f;
        syncVessel();
        placePlayerAtHelm(player);
    }

    private void syncVessel() {
        vessel.setPosition(x, y, z).setYaw(yaw);
    }

    private void placePlayerAtHelm(PlayerController player) {
        // The deck is authored 0.78 units above the vessel origin.
        player.setExternalPose(x, y + 0.80f, z, yaw, pitch);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
'''

files["game-client/src/main/java/dev/itzsiden/skyvoxel/client/ui/ShipMenuOverlay.java"] = r'''package dev.itzsiden.skyvoxel.client.ui;

import dev.itzsiden.skyvoxel.render.api.UiDrawList;

/** Modal harbor ship-selection overlay. */
public final class ShipMenuOverlay {
    public enum Action { NONE, CLOSE, SUMMON }

    private record Layout(float x, float y, float width, float height,
                          float closeX, float closeY, float buttonX, float buttonY,
                          float buttonWidth, float buttonHeight, float scale) { }

    public UiDrawList build(int width, int height, double mouseX, double mouseY) {
        Layout l = layout(width, height);
        UiDrawList.Builder draw = new UiDrawList.Builder();
        draw.fillRect(0, 0, width, height, 0x99050B12);
        draw.roundedRect(l.x(), l.y(), l.width(), l.height(), 0xF014202A, 14.0f * l.scale());
        draw.strokeRoundedRect(l.x(), l.y(), l.width(), l.height(), 0xFF527181, 1.5f * l.scale(), 14.0f * l.scale());

        draw.fillRect(l.x() + 1.0f, l.y() + 1.0f, l.width() - 2.0f, 4.0f * l.scale(), 0xFF58C9D5);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 43.0f * l.scale(),
            "HARBOR MASTER", 10.0f * l.scale(), 0xFF8DD9EA, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 79.0f * l.scale(),
            "Ship Menu", 27.0f * l.scale(), 0xFFF4F6F8, true, true, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 30.0f * l.scale(), l.y() + 106.0f * l.scale(),
            "Captain Rowan  /  Iskut Harbor", 12.0f * l.scale(), 0xFFB5C5D1, false, false, UiDrawList.TextAlign.LEFT);

        draw.roundedRect(l.x() + 28.0f * l.scale(), l.y() + 132.0f * l.scale(),
            l.width() - 56.0f * l.scale(), 92.0f * l.scale(), 0xD91B2A37, 9.0f * l.scale());
        draw.roundedRect(l.x() + 46.0f * l.scale(), l.y() + 151.0f * l.scale(),
            56.0f * l.scale(), 54.0f * l.scale(), 0xFF294B58, 8.0f * l.scale());
        draw.text(l.x() + 74.0f * l.scale(), l.y() + 186.0f * l.scale(),
            "SAIL", 13.0f * l.scale(), 0xFF8DEBFF, true, true, UiDrawList.TextAlign.CENTER);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 164.0f * l.scale(),
            "HARBOR CUTTER", 14.0f * l.scale(), 0xFFF0F4F7, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 188.0f * l.scale(),
            "A nimble vessel for the outer islands.", 11.0f * l.scale(), 0xFFB5C5D1, false, false, UiDrawList.TextAlign.LEFT);
        draw.text(l.x() + 122.0f * l.scale(), l.y() + 207.0f * l.scale(),
            "No fare required", 10.0f * l.scale(), 0xFF79D4AF, true, false, UiDrawList.TextAlign.LEFT);

        boolean hovered = contains(mouseX, mouseY, l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight());
        draw.roundedRect(l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight(),
            hovered ? 0xFF287487 : 0xFF1D5969, 8.0f * l.scale());
        draw.strokeRoundedRect(l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight(),
            hovered ? 0xFFB3F4FF : 0xFF4F9CAC, 1.2f * l.scale(), 8.0f * l.scale());
        draw.text(l.buttonX() + l.buttonWidth() * 0.5f, l.buttonY() + l.buttonHeight() * 0.5f + 5.0f * l.scale(),
            "SUMMON VESSEL", 14.0f * l.scale(), 0xFFFFFFFF, true, true, UiDrawList.TextAlign.CENTER);

        draw.roundedRect(l.closeX(), l.closeY(), 30.0f * l.scale(), 30.0f * l.scale(), 0xFF293945, 7.0f * l.scale());
        draw.text(l.closeX() + 15.0f * l.scale(), l.closeY() + 20.0f * l.scale(),
            "X", 13.0f * l.scale(), 0xFFD6E1E8, true, false, UiDrawList.TextAlign.CENTER);
        draw.text(l.x() + l.width() * 0.5f, l.y() + l.height() - 15.0f * l.scale(),
            "W / S  SAIL     A / D  STEER     E  LEAVE THE HELM NEAR LAND",
            9.0f * l.scale(), 0xFF8FA3B0, true, false, UiDrawList.TextAlign.CENTER);
        return draw.build();
    }

    public UiDrawList buildHud(int width, int height, float speed) {
        float scale = scale(width, height);
        float w = 430.0f * scale;
        float h = 38.0f * scale;
        float x = (width - w) * 0.5f;
        float y = height - 68.0f * scale;
        UiDrawList.Builder draw = new UiDrawList.Builder();
        draw.roundedRect(x, y, w, h, 0xC9121D27, 8.0f * scale);
        draw.strokeRoundedRect(x, y, w, h, 0x7755C9D5, 1.0f * scale, 8.0f * scale);
        draw.text(x + 12.0f * scale, y + 23.0f * scale,
            "HELM  /  W S SAIL  •  A D STEER  •  E DOCK", 10.0f * scale,
            0xFFEAF2F6, true, false, UiDrawList.TextAlign.LEFT);
        draw.text(x + w - 12.0f * scale, y + 23.0f * scale,
            String.format(java.util.Locale.ROOT, "%.1f u/s", Math.abs(speed)),
            10.0f * scale, 0xFF8DEBFF, true, false, UiDrawList.TextAlign.RIGHT);
        return draw.build();
    }

    public Action handleClick(int width, int height, double mouseX, double mouseY) {
        Layout l = layout(width, height);
        if (contains(mouseX, mouseY, l.closeX(), l.closeY(), 30.0f * l.scale(), 30.0f * l.scale())) {
            return Action.CLOSE;
        }
        if (contains(mouseX, mouseY, l.buttonX(), l.buttonY(), l.buttonWidth(), l.buttonHeight())) {
            return Action.SUMMON;
        }
        return Action.NONE;
    }

    private static Layout layout(int width, int height) {
        float scale = scale(width, height);
        float panelWidth = Math.min(width - 28.0f, 520.0f * scale);
        float panelHeight = Math.min(height - 28.0f, 360.0f * scale);
        float x = (width - panelWidth) * 0.5f;
        float y = (height - panelHeight) * 0.5f;
        float buttonWidth = Math.min(panelWidth - 56.0f * scale, 300.0f * scale);
        float buttonHeight = 46.0f * scale;
        return new Layout(x, y, panelWidth, panelHeight,
            x + panelWidth - 48.0f * scale, y + 18.0f * scale,
            x + (panelWidth - buttonWidth) * 0.5f, y + panelHeight - 76.0f * scale,
            buttonWidth, buttonHeight, scale);
    }

    private static float scale(int width, int height) {
        return Math.max(0.72f, Math.min(1.0f, Math.min(width / 1280.0f, height / 720.0f)));
    }

    private static boolean contains(double x, double y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
}
'''

files["data/entities/models/captain_rowan.vmodel"] = r'''# Iskut harbor shipwright. Coordinates are local to the feet origin.
SKYVOXEL_ENTITY_MODEL 1
NAME Captain_Rowan
HEIGHT 2.05
PART boots_left brown -0.15 0.13 0.00 0.24 0.26 0.34 0
PART boots_right brown 0.15 0.13 0.00 0.24 0.26 0.34 0
PART trousers navy 0.00 0.52 0.00 0.48 0.62 0.34 0
PART coat teal 0.00 1.08 0.00 0.62 0.66 0.38 0
PART belt brown 0.00 0.78 -0.01 0.64 0.12 0.40 0
PART head skin 0.00 1.57 -0.01 0.40 0.40 0.40 0
PART hair brown 0.00 1.76 0.02 0.43 0.16 0.43 0
PART hat_brim gold 0.00 1.88 0.00 0.58 0.12 0.58 0
PART hat_top navy 0.00 2.02 0.00 0.40 0.18 0.40 0
PART arm_left teal -0.40 1.08 0.00 0.20 0.60 0.26 0
PART arm_right teal 0.40 1.08 0.00 0.20 0.60 0.26 0
PART shoulder_badge gold -0.26 1.35 -0.20 0.16 0.18 0.06 0
'''

files["data/entities/models/harbor_cutter.vmodel"] = r'''# Player-controlled low-poly harbor cutter. Forward is local -Z.
SKYVOXEL_ENTITY_MODEL 1
NAME Harbor_Cutter
HEIGHT 3.10
PART keel dark 0.00 0.12 0.00 1.55 0.28 5.40 0
PART hull wood 0.00 0.34 0.00 2.85 0.50 6.20 0
PART deck cream 0.00 0.69 0.00 2.35 0.18 4.90 0
PART rail_left brown -1.32 0.76 0.00 0.22 0.72 5.75 0
PART rail_right brown 1.32 0.76 0.00 0.22 0.72 5.75 0
PART stern_plank wood_light 0.00 0.79 2.86 2.45 0.56 0.28 0
PART bow_plank wood_light 0.00 0.70 -3.03 2.00 0.42 0.42 0
PART bow_tip gold 0.00 0.55 -3.23 0.80 0.32 0.38 0
PART seat wood_light 0.00 0.88 1.25 0.92 0.18 0.42 0
PART mast wood 0.00 1.86 -0.35 0.18 2.35 0.18 0
PART sail cream 0.00 1.90 -0.58 0.10 2.00 1.82 0
PART boom brown 0.00 0.98 -0.58 2.10 0.14 0.14 0
PART lantern gold 0.00 1.18 2.72 0.28 0.32 0.28 0
'''

files["data/structures/ocean_surface.vstruct"] = r'''# Dedicated visual-only ocean sheet. Large enough for long-distance sailing.
SKYVOXEL_STRUCTURE 1
NAME Ocean_Surface
PLANE ocean_water water 0 0 0 4096 4096
'''

# Write patch payload files.
for rel, content in files.items():
    target = patch / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8", newline="\n")

ps1 = r'''param(
    [Parameter(Mandatory=$true)][string]$ProjectRoot,
    [Parameter(Mandatory=$true)][string]$PatchRoot
)

$ErrorActionPreference = "Stop"
$ProjectRoot = [System.IO.Path]::GetFullPath($ProjectRoot)
$PatchRoot = [System.IO.Path]::GetFullPath($PatchRoot)

function Replace-Once([string]$Source, [string]$Old, [string]$New, [string]$Label) {
    $count = [regex]::Matches($Source, [regex]::Escape($Old)).Count
    if ($count -ne 1) {
        throw "Patch anchor '$Label' expected exactly once, found $count. No source files have been changed."
    }
    return $Source.Replace($Old, $New)
}

$clientPath = Join-Path $ProjectRoot "game-client\src\main\java\dev\itzsiden\skyvoxel\client\ClientMain.java"
$playerPath = Join-Path $ProjectRoot "game-client\src\main\java\dev\itzsiden\skyvoxel\client\PlayerController.java"
$oceanPath = Join-Path $ProjectRoot "data\structures\ocean_surface.vstruct"
$payloadRoot = Join-Path $PatchRoot "patch"

foreach ($required in @($clientPath, $playerPath, $oceanPath, (Join-Path $ProjectRoot "settings.gradle.kts"))) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Could not find '$required'. Extract the patch and run it against the Iskut project root."
    }
}

$client = [System.IO.File]::ReadAllText($clientPath)
$player = [System.IO.File]::ReadAllText($playerPath)
$client = $client.Replace("`r`n", "`n").Replace("`r", "`n")
$player = $player.Replace("`r`n", "`n").Replace("`r", "`n")

# All source edits are prepared and validated in memory before backups or writes.
$client = Replace-Once $client 'import dev.itzsiden.skyvoxel.client.ui.InventoryOverlay;' `
    ("import dev.itzsiden.skyvoxel.client.ui.InventoryOverlay;" + "`nimport dev.itzsiden.skyvoxel.client.ui.ShipMenuOverlay;") 'ShipMenuOverlay import'

$client = Replace-Once $client '                StaticCollisionWorld collisionWorld = new StaticCollisionWorld(scene);' `
    ("                if (loadedMap != null) IslandArchipelago.populate(scene, renderScene);" + "`n                StaticCollisionWorld collisionWorld = new StaticCollisionWorld(scene);") 'archipelago initialization'

$client = Replace-Once $client '                EntityModel wispModel = entityModelLoader.load(dataPath("data/entities/models/sky_wisp.vmodel"));' `
    ('                EntityModel wispModel = entityModelLoader.load(dataPath("data/entities/models/sky_wisp.vmodel"));' + "`n" +
     '                EntityModel captainModel = entityModelLoader.load(dataPath("data/entities/models/captain_rowan.vmodel"));' + "`n" +
     '                EntityModel shipModel = entityModelLoader.load(dataPath("data/entities/models/harbor_cutter.vmodel"));') 'new entity models'

$client = Replace-Once $client '                    .setPosition(-5.5f, 1.18f, -3.5f).setYaw(0.0f).setHealth(20, 20).setShowHealthBar(false));' `
    ('                    .setPosition(-5.5f, 1.18f, -3.5f).setYaw(0.0f).setHealth(20, 20).setShowHealthBar(false));' + "`n" +
     '                Entity shipwright = new Entity("npc-shipwright-rowan", EntityType.NPC, captainModel, "Captain Rowan", "SHIPWRIGHT • HARBOR")' + "`n" +
     '                    .setPosition(-8.5f, 1.18f, 59.8f).setYaw(0.0f).setHealth(20, 20).setShowHealthBar(false);' + "`n" +
     '                entityWorld.add(shipwright);') 'shipwright NPC'

$client = Replace-Once $client '                HostileMobSystem hostileMobs = new HostileMobSystem();' `
    ('                entityInteractions.register("npc-shipwright-rowan", "Open Ship Menu", npc -> "Choose a vessel and sail to the outer islands.");' + "`n" +
     '                HostileMobSystem hostileMobs = new HostileMobSystem();') 'shipwright interaction registration'

$client = Replace-Once $client '                EntityRenderSystem entityRenderSystem = new EntityRenderSystem();' `
    ('                EntityRenderSystem entityRenderSystem = new EntityRenderSystem();' + "`n" +
     '                ShipExplorationSystem shipExploration = new ShipExplorationSystem(entityWorld, shipModel);' + "`n" +
     '                ShipMenuOverlay shipMenu = new ShipMenuOverlay();') 'ship systems'

$client = Replace-Once $client '                inventoryUi.setItemVisuals(itemVisuals);' `
    ('                inventoryUi.setItemVisuals(itemVisuals);' + "`n" +
     '                boolean shipMenuOpen = false;') 'ship menu state'

$client = Replace-Once $client '                    MousePosition cursor = window.cursorPosition();' `
    ('                    MousePosition cursor = window.cursorPosition();' + "`n" +
     '                    boolean shipMenuClickHandled = false;') 'ship menu click state'

$client = Replace-Once $client '                    if (screen == GameScreen.PLAYING && inventoryDown && !previousInventoryDown) {' `
    '                    if (screen == GameScreen.PLAYING && inventoryDown && !previousInventoryDown && !shipMenuOpen) {' 'inventory key guard'

$menuClick = @'
                    if (screen == GameScreen.PLAYING && shipMenuOpen && leftClicked) {
                        shipMenuClickHandled = true;
                        ShipMenuOverlay.Action action = shipMenu.handleClick(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y());
                        if (action == ShipMenuOverlay.Action.SUMMON) {
                            shipExploration.summonAndBoard(player);
                            shipMenuOpen = false;
                            window.setMouseCaptured(true);
                            interactionMessage = "Vessel summoned. W/S sail • A/D steer • E disembark near land.";
                            interactionMessageUntil = System.nanoTime() + 4_000_000_000L;
                        } else if (action == ShipMenuOverlay.Action.CLOSE) {
                            shipMenuOpen = false;
                            window.setMouseCaptured(true);
                        }
                    }
'@
$client = Replace-Once $client '                    previousInventoryDown = inventoryDown;' `
    ('                    previousInventoryDown = inventoryDown;' + "`n" + $menuClick.TrimEnd("`r","`n")) 'ship menu button handling'

$client = Replace-Once $client '                    if (screen == GameScreen.PLAYING && escapePressed) {' `
    ('                    if (screen == GameScreen.PLAYING && escapePressed && shipMenuOpen) {' + "`n" +
     '                        shipMenuOpen = false;' + "`n" +
     '                        window.setMouseCaptured(true);' + "`n" +
     '                    } else if (screen == GameScreen.PLAYING && escapePressed) {') 'escape closes ship menu'

$client = Replace-Once $client '                    player.setInputEnabled(playerStats.alive() && !inventoryOpen && respawnAtNanos == 0L);' `
    ('                    boolean ridingVessel = shipExploration.boarded();' + "`n" +
     '                    player.setInputEnabled(playerStats.alive() && !inventoryOpen && !shipMenuOpen && !ridingVessel && respawnAtNanos == 0L);') 'player input gating'

$client = Replace-Once $client '                    player.update(window, deltaSeconds);' `
    ('                    if (ridingVessel && playerStats.alive() && respawnAtNanos == 0L) {' + "`n" +
     '                        shipExploration.update(window, deltaSeconds, player);' + "`n" +
     '                    } else {' + "`n" +
     '                        player.update(window, deltaSeconds);' + "`n" +
     '                    }') 'ship movement update'

$client = Replace-Once $client '                    Optional<EntityRaycast.Hit> entityHit = inventoryOpen ? Optional.empty() : EntityRaycast.raycast(' `
    '                    Optional<EntityRaycast.Hit> entityHit = (inventoryOpen || shipMenuOpen || shipMenuClickHandled) ? Optional.empty() : EntityRaycast.raycast(' 'raycast modal guard'

$client = Replace-Once $client '                        !inventoryOpen && !uiMouseHandled && leftClicked && entityHit.isEmpty(),' `
    '                        !inventoryOpen && !shipMenuOpen && !shipMenuClickHandled && !uiMouseHandled && leftClicked && entityHit.isEmpty(),' 'world click modal guard'

$client = Replace-Once $client '                        !inventoryOpen && !uiMouseHandled && rightClicked && entityHit.isEmpty());' `
    '                        !inventoryOpen && !shipMenuOpen && !shipMenuClickHandled && !uiMouseHandled && rightClicked && entityHit.isEmpty());' 'world right-click modal guard'

$client = Replace-Once $client '                    if (!inventoryOpen && !uiMouseHandled && leftClicked && entityHit.isPresent()) {' `
    '                    if (!inventoryOpen && !shipMenuOpen && !shipMenuClickHandled && !uiMouseHandled && leftClicked && entityHit.isPresent()) {' 'entity click modal guard'

$client = Replace-Once $client '                        if (clicked.type() == EntityType.MOB) {' `
    ('                        if (clicked.type() == EntityType.NPC && clicked.id().equals("npc-shipwright-rowan")) {' + "`n" +
     '                            shipMenuOpen = true;' + "`n" +
     '                            window.setMouseCaptured(false);' + "`n" +
     '                            profilePanel.setVisible(false);' + "`n" +
     '                        } else if (clicked.type() == EntityType.MOB) {') 'click shipwright NPC'

$client = Replace-Once $client '                    if (!inventoryOpen && interactPressed && entityHit.isPresent() && entityHit.get().entity().type() == EntityType.NPC) {' `
    ('                    if (!inventoryOpen && !shipMenuOpen && interactPressed && shipExploration.boarded()) {' + "`n" +
     '                        boolean leftVessel = shipExploration.disembark(player);' + "`n" +
     '                        interactionMessage = leftVessel' + "`n" +
     '                            ? "You left the helm. Press E near your vessel to board again."' + "`n" +
     '                            : "Sail close to the harbor or an island before disembarking."' + "`n" +
     '                        interactionMessageUntil = System.nanoTime() + 3_000_000_000L;' + "`n" +
     '                    } else if (!inventoryOpen && !shipMenuOpen && interactPressed && shipExploration.canBoard(player)) {' + "`n" +
     '                        shipExploration.board(player);' + "`n" +
     '                        window.setMouseCaptured(true);' + "`n" +
     '                        interactionMessage = "Vessel boarded. W/S sail • A/D steer • E disembark near land."' + "`n" +
     '                        interactionMessageUntil = System.nanoTime() + 3_000_000_000L;' + "`n" +
     '                    } else if (!inventoryOpen && !shipMenuOpen && interactPressed && entityHit.isPresent() && entityHit.get().entity().type() == EntityType.NPC) {') 'ship E interactions'

$client = Replace-Once $client '                        interactionMessage = entityInteractions.interact(npc).orElse("This NPC has no interaction configured yet.");' `
    ('                        if (npc.id().equals("npc-shipwright-rowan")) {' + "`n" +
     '                            shipMenuOpen = true;' + "`n" +
     '                            window.setMouseCaptured(false);' + "`n" +
     '                            interactionMessage = "Captain Rowan: Pick a vessel and explore beyond the harbor.";' + "`n" +
     '                        } else {' + "`n" +
     '                            interactionMessage = entityInteractions.interact(npc).orElse("This NPC has no interaction configured yet.");' + "`n" +
     '                        }') 'E opens ship menu'

$client = Replace-Once $client '                    } else if (!inventoryOpen && interactPressed && loadedMap != null && targeting.target() != null && entityHit.isEmpty()) {' `
    '                    } else if (!inventoryOpen && !shipMenuOpen && interactPressed && loadedMap != null && targeting.target() != null && entityHit.isEmpty()) {' 'map interaction modal guard'

$client = Replace-Once $client '                    if (entityHit.isPresent() && entityWorld.get(entityHit.get().entity().id()) != null) {' `
    ('                    if (shipExploration.boarded()) {' + "`n" +
     '                        targetStatus = "[E] Disembark near land  •  W/S sail  •  A/D steer";' + "`n" +
     '                    } else if (entityHit.isPresent() && entityWorld.get(entityHit.get().entity().id()) != null) {') 'helm target prompt'

$client = Replace-Once $client '                    if (promptNow < interactionMessageUntil) {' `
    ('                    if (shipExploration.boarded()) {' + "`n" +
     '                        prompt = "[E] Disembark near land  •  W/S sail  •  A/D steer";' + "`n" +
     '                    } else if (promptNow < interactionMessageUntil) {') 'helm interaction prompt'

$client = Replace-Once $client '                    UiDrawList.Builder combinedUi = new UiDrawList.Builder()' `
    '                    UiDrawList.Builder combinedUi = new UiDrawList.Builder()' 'combined UI anchor'

$client = Replace-Once $client '                    renderer.drawUi(combinedUi.build());' `
    ('                    if (shipExploration.boarded()) combinedUi.addAll(shipMenu.buildHud(uiWidth, uiHeight, shipExploration.speed()));' + "`n" +
     '                    if (shipMenuOpen) combinedUi.addAll(shipMenu.build(uiWidth, uiHeight, cursor.x(), cursor.y()));' + "`n" +
     '                    renderer.drawUi(combinedUi.build());') 'ship UI rendering'

$player = Replace-Once $player '    void setInputEnabled(boolean enabled) { inputEnabled = enabled; }' `
    ('    void setInputEnabled(boolean enabled) { inputEnabled = enabled; }' + "`n" +
     '    void setExternalPose(double px, double py, double pz, double externalYaw, double externalPitch) {' + "`n" +
     '        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)' + "`n" +
     '            || !Double.isFinite(externalYaw) || !Double.isFinite(externalPitch)) {' + "`n" +
     '            throw new IllegalArgumentException("External player pose must be finite.");' + "`n" +
     '        }' + "`n" +
     '        x = px; feetY = py; z = pz; yaw = externalYaw;' + "`n" +
     '        double pitchLimit = Math.toRadians(75.0);' + "`n" +
     '        pitch = Math.max(-pitchLimit, Math.min(pitchLimit, externalPitch));' + "`n" +
     '        velocityX = velocityZ = verticalVelocity = 0.0;' + "`n" +
     '        horizontalSpeed = 0.0; moving = sprinting = crouching = false; grounded = true;' + "`n" +
     '        previousSpaceDown = false; jumpBufferRemaining = 0.0; coyoteRemaining = COYOTE_SECONDS;' + "`n" +
     '        peakAirY = feetY; pendingFallDamage = 0.0f; cameraBob = landingDip = stepCameraLag = 0.0;' + "`n" +
     '    }') 'external player pose API'

# Prepare complete source text before touching the project.
backup_dir = Join-Path $ProjectRoot (".iskut-patch-backups\ship-exploration-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force -Path $backup_dir | Out-Null
Copy-Item -LiteralPath $clientPath -Destination (Join-Path $backup_dir "ClientMain.java")
Copy-Item -LiteralPath $playerPath -Destination (Join-Path $backup_dir "PlayerController.java")
Copy-Item -LiteralPath $oceanPath -Destination (Join-Path $backup_dir "ocean_surface.vstruct")

$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($clientPath, $client, $utf8)
[System.IO.File]::WriteAllText($playerPath, $player, $utf8)

Get-ChildItem -LiteralPath $payloadRoot -Recurse -File | ForEach-Object {
    $relative = $_.FullName.Substring($payloadRoot.Length).TrimStart('\','/')
    $destination = Join-Path $ProjectRoot $relative
    $destinationDirectory = Split-Path -Parent $destination
    New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $destination -Force
}

Write-Host ""
Write-Host "Iskut Ship Exploration patch applied." -ForegroundColor Green
Write-Host "Backup: $backup_dir"
Write-Host "Next: run :game-client:compileJava (the .bat does this automatically)."
'''
(root / "Apply-Iskut-Patch.ps1").write_text(ps1, encoding="utf-8", newline="\n")

bat = r'''@echo off
setlocal
title Iskut - Ship Exploration Patch

set "PROJECT_ROOT=%~dp0"
if not exist "%PROJECT_ROOT%settings.gradle.kts" (
    echo Iskut project root was not detected automatically.
    set /p "PROJECT_ROOT=Enter the full path to your Iskut folder: "
)
if not exist "%PROJECT_ROOT%\settings.gradle.kts" (
    echo.
    echo ERROR: settings.gradle.kts was not found. Nothing was patched.
    pause
    exit /b 1
)

echo.
echo [1/2] Applying Ship Exploration patch...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Apply-Iskut-Patch.ps1" -ProjectRoot "%PROJECT_ROOT%" -PatchRoot "%~dp0"
if errorlevel 1 (
    echo.
    echo Patch failed. Check the message above. Existing source files were not overwritten if an anchor check failed.
    pause
    exit /b 1
)

echo.
echo [2/2] Compiling game-client...
pushd "%PROJECT_ROOT%"
call gradlew.bat :game-client:compileJava
set "BUILD_RESULT=%ERRORLEVEL%"
popd

echo.
if not "%BUILD_RESULT%"=="0" (
    echo Compile failed. Your original source files are backed up in .iskut-patch-backups.
    echo Send the compile output so the patch can be corrected.
) else (
    echo SUCCESS: Ship Exploration patch applied and game-client compiled.
    echo Run run-client-opengl.bat to test it.
)
pause
exit /b %BUILD_RESULT%
'''
(root / "Apply-Iskut-Patch.bat").write_text(bat, encoding="utf-8", newline="\n")

readme = r'''ISKUT SHIP EXPLORATION PATCH
============================

Features
--------
- Adds Captain Rowan, a shipwright NPC beside the harbor dock.
- Clicking Captain Rowan or pressing E opens the Ship Menu.
- The menu summons a controllable Harbor Cutter.
- W/S controls forward/reverse sail, A/D steers, mouse controls the view.
- E disembarks only when the vessel is close to the harbor or one of the three islands.
- The vessel remains available nearby so the player can press E to board again.
- Adds three small handcrafted island destinations with collision.
- Expands the visual ocean plane to 4096 x 4096.
- Uses Iskut's existing EntityRenderSystem and collision pipeline.

Install
-------
1. Extract the ZIP directly into the Iskut project root (the folder containing settings.gradle.kts).
2. Double-click Apply-Iskut-Patch.bat.
3. The installer makes timestamped backups in .iskut-patch-backups.
4. It applies source changes and runs :game-client:compileJava.
5. On success, run run-client-opengl.bat.

Important
---------
- Close the game before applying the patch.
- The patch is anchored to the current ItzSiden/Iskut main-branch source layout.
- If an anchor is missing or appears more than once, the installer stops before changing source files.
- If compilation fails, keep the console output and the backup folder. Do not delete the backups.
- This is a local patch package; it does not commit changes to GitHub.

Controls
--------
W / S       Sail forward / reverse
A / D       Steer
Mouse       Look around while at the helm
E           Disembark near land / board the nearby vessel
ESC         Close Ship Menu
'''
(root / "README.txt").write_text(readme, encoding="utf-8", newline="\n")

# Remove any previous archive in this location and create a deterministic package.
zip_path = Path("/mnt/data/Iskut_Ship_Exploration_Patch.zip")
if zip_path.exists():
    zip_path.unlink()

with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    for path in sorted(root.rglob("*")):
        if path.is_file():
            zf.write(path, path.relative_to(root.parent))

# Package sanity checks.
with zipfile.ZipFile(zip_path, "r") as zf:
    names = zf.namelist()
    assert "Iskut_Ship_Exploration_Patch/Apply-Iskut-Patch.bat" in names
    assert "Iskut_Ship_Exploration_Patch/Apply-Iskut-Patch.ps1" in names
    assert "Iskut_Ship_Exploration_Patch/patch/game-client/src/main/java/dev/itzsiden/skyvoxel/client/ShipExplorationSystem.java" in names
    assert "Iskut_Ship_Exploration_Patch/patch/data/entities/models/harbor_cutter.vmodel" in names
    assert "Iskut_Ship_Exploration_Patch/patch/data/structures/ocean_surface.vstruct" in names

digest = hashlib.sha256(zip_path.read_bytes()).hexdigest()
print(f"Created: {zip_path}")
print(f"Size: {zip_path.stat().st_size:,} bytes")
print(f"SHA-256: {digest}")
print(f"Archive files: {len(names)}")
