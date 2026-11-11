package dev.itzsiden.skyvoxel.client;

import dev.itzsiden.skyvoxel.engine.camera.Camera;
import dev.itzsiden.skyvoxel.engine.entity.Entity;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.entity.EntityRenderSystem;
import dev.itzsiden.skyvoxel.engine.entity.EntityType;
import dev.itzsiden.skyvoxel.engine.entity.EntityWorld;
import dev.itzsiden.skyvoxel.engine.gameplay.EquipmentLoadout;
import dev.itzsiden.skyvoxel.engine.gameplay.PlayerStats;
import dev.itzsiden.skyvoxel.engine.gameplay.PlayerProfileSnapshot;
import dev.itzsiden.skyvoxel.engine.items.InventoryModel;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionLoader;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionRegistry;
import dev.itzsiden.skyvoxel.engine.items.ItemStack;
import dev.itzsiden.skyvoxel.engine.items.ItemUseResult;
import dev.itzsiden.skyvoxel.engine.items.ItemUseService;
import dev.itzsiden.skyvoxel.engine.persistence.JsonFilePlayerDataRepository;
import dev.itzsiden.skyvoxel.engine.persistence.PlayerDataRepository;
import dev.itzsiden.skyvoxel.engine.persistence.PlayerDataSnapshot;
import dev.itzsiden.skyvoxel.client.ui.InventoryOverlay;
import dev.itzsiden.skyvoxel.client.map.GameMap;
import dev.itzsiden.skyvoxel.client.map.MapLoader;
import dev.itzsiden.skyvoxel.client.entity.EntityModelLoader;
import dev.itzsiden.skyvoxel.client.entity.EntityHologramRenderer;
import dev.itzsiden.skyvoxel.client.entity.EntityInteractionRegistry;
import dev.itzsiden.skyvoxel.client.items.ItemVisualRegistry;
import dev.itzsiden.skyvoxel.client.items.HeldItemViewModel;
import dev.itzsiden.skyvoxel.client.items.ItemWorldRenderSystem;
import dev.itzsiden.skyvoxel.engine.physics.RaycastHit;
import dev.itzsiden.skyvoxel.engine.physics.EntityRaycast;
import dev.itzsiden.skyvoxel.engine.physics.StaticCollisionWorld;
import dev.itzsiden.skyvoxel.engine.render.SceneRenderer;
import dev.itzsiden.skyvoxel.engine.render.SceneRenderStats;
import dev.itzsiden.skyvoxel.engine.scene.Scene;
import dev.itzsiden.skyvoxel.engine.scene.Mesh;
import dev.itzsiden.skyvoxel.engine.scene.Material;
import dev.itzsiden.skyvoxel.engine.scene.RenderObject;
import dev.itzsiden.skyvoxel.engine.scene.Transform;
import dev.itzsiden.skyvoxel.render.api.Key;
import dev.itzsiden.skyvoxel.render.api.MouseButton;
import dev.itzsiden.skyvoxel.render.api.MousePosition;
import dev.itzsiden.skyvoxel.render.api.RenderFrame;
import dev.itzsiden.skyvoxel.render.api.RenderQuality;
import dev.itzsiden.skyvoxel.render.api.Renderer;
import dev.itzsiden.skyvoxel.render.api.RendererProvider;
import dev.itzsiden.skyvoxel.render.api.SkyboxSettings;
import dev.itzsiden.skyvoxel.render.api.RenderWindow;
import dev.itzsiden.skyvoxel.render.api.TextureData;
import dev.itzsiden.skyvoxel.render.api.WindowConfig;
import dev.itzsiden.skyvoxel.client.ui.GuiAssets;
import dev.itzsiden.skyvoxel.client.ui.LoadingScreen;
import dev.itzsiden.skyvoxel.client.ui.MainMenuScreen;
import dev.itzsiden.skyvoxel.client.ui.PauseMenuScreen;
import dev.itzsiden.skyvoxel.client.ui.SettingsScreen;
import dev.itzsiden.skyvoxel.ui.UiAnchor;
import dev.itzsiden.skyvoxel.ui.UiCrosshair;
import dev.itzsiden.skyvoxel.ui.UiLabel;
import dev.itzsiden.skyvoxel.ui.UiPanel;
import dev.itzsiden.skyvoxel.ui.UiProgressBar;
import dev.itzsiden.skyvoxel.ui.UiRoot;
import dev.itzsiden.skyvoxel.render.api.UiDrawList;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Map;
import java.util.ServiceLoader;

public final class ClientMain {
    private static final float BASE_FOV = (float) Math.toRadians(72.0);
    private static final float SPRINT_FOV = (float) Math.toRadians(82.0);

    private ClientMain() {}

    public static void main(String[] args) throws IOException {
        configureRenderScale(args);
        int benchmarkCount = benchmarkCount(args);
        boolean culling = !hasArg(args, "--no-culling");
        // Procedural terrain is now an opt-in renderer demo only. Normal startup loads a hand-authored .vmap.
        boolean voxelDemo = hasArg(args, "--voxel");
        boolean debugDemo = hasArg(args, "--demo") || benchmarkCount > 0;
        float viewDistance = viewDistance(args);
        applyRendererArgs(args);
        String requestedBackend = System.getProperty("skyvoxel.renderer",
            System.getenv().getOrDefault("SKYVOXEL_RENDERER", "software"));
        RendererProvider provider = ServiceLoader.load(RendererProvider.class)
            .stream().map(ServiceLoader.Provider::get)
            .filter(candidate -> candidate.id().equalsIgnoreCase(requestedBackend))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Renderer backend '" + requestedBackend + "' was not found. Check the runtime dependencies."));

        try (Renderer renderer = provider.createRenderer()) {
            System.out.println("Starting Iskut with " + renderer.description());
            RenderWindow window = renderer.createWindow(new WindowConfig(1280, 720, "Iskut | Ocean World"));
            window.setMouseCaptured(false);
            LoadingScreen loadingScreen = new LoadingScreen(
                GuiAssets.loadTexture(dataPath("data/contents/background_loading.png")));
            loadingScreen.present(renderer, window, 0.02f, "Preparing game...");
            TextureData mainMenuBackground = GuiAssets.loadTexture(dataPath("data/contents/background_main.png"));
            TextureData gameLogo = GuiAssets.loadTexture(dataPath("data/contents/logo.png"));
            MainMenuScreen mainMenu = new MainMenuScreen(mainMenuBackground, gameLogo);
            PauseMenuScreen pauseMenu = new PauseMenuScreen(mainMenuBackground);
            SettingsScreen settingsMenu = new SettingsScreen(mainMenuBackground);
            loadingScreen.present(renderer, window, 0.10f, "Loading interface...");

            try (SceneRenderer sceneRenderer = new SceneRenderer(renderer)
                    .setFrustumCullingEnabled(culling)
                    .setMaxDrawDistance(viewDistance + 8.0f);
                 SceneRenderer entitySceneRenderer = new SceneRenderer(renderer)
                    .setFrustumCullingEnabled(culling)
                    .setMaxDrawDistance(viewDistance + 4.0f);
                 SceneRenderer viewModelSceneRenderer = new SceneRenderer(renderer)
                    .setFrustumCullingEnabled(false).setFrontToBackSorting(false)) {
                GameMap loadedMap = null;
                Scene scene;
                Scene renderScene;
                if (voxelDemo) {
                    scene = SceneFactory.createVoxelDemo();
                    renderScene = scene;
                    System.out.println("[Iskut] Procedural voxel terrain is DEMO ONLY. Omit --voxel to load the handcrafted map.");
                } else if (debugDemo) {
                    scene = SceneFactory.createDemo(benchmarkCount);
                    renderScene = scene;
                    System.out.println("[Iskut] Running renderer debug scene. Omit --demo/--benchmark to load the handcrafted map.");
                } else {
                    loadedMap = MapLoader.load(mapPath(args));
                    scene = loadedMap.scene();
                    renderScene = loadedMap.renderScene();
                    System.out.println("[Iskut] Loaded handcrafted map: " + loadedMap.name()
                        + " | objects: " + scene.size() + " | render batches: " + renderScene.size()
                        + " | interactables: " + loadedMap.interactions().size());
                }
                loadingScreen.present(renderer, window, 0.30f, "Building the world...");
                StaticCollisionWorld collisionWorld = new StaticCollisionWorld(scene);
                PlayerController player = loadedMap == null
                    ? new PlayerController(collisionWorld)
                    : new PlayerController(collisionWorld, loadedMap.spawn());
                WorldTargeting targeting = new WorldTargeting(scene, collisionWorld);

                // Entity models are reusable .vmodel templates; each entity owns only transform/state.
                Mesh entityCube = new Mesh("entity-shared-unit-cuboid", CuboidMeshFactory.createUnitCuboid());
                EntityModelLoader entityModelLoader = new EntityModelLoader(entityCube);
                EntityModel playerModel = entityModelLoader.load(dataPath("data/entities/models/astral_traveler.vmodel"));
                EntityModel guideModel = entityModelLoader.load(dataPath("data/entities/models/guide_elyra.vmodel"));
                EntityModel wispModel = entityModelLoader.load(dataPath("data/entities/models/sky_wisp.vmodel"));
                EntityWorld entityWorld = new EntityWorld();
                Entity localPlayerEntity = new Entity("player-local", EntityType.PLAYER, playerModel, "You", "LOCAL PLAYER")
                    .setModelVisible(false).setShowNameplate(false).setShowHealthBar(false)
                    .setPosition((float) player.x(), (float) player.feetY(), (float) player.z());
                entityWorld.add(localPlayerEntity);
                entityWorld.add(new Entity("player-demo-01", EntityType.PLAYER, playerModel, "Astra", "PLAYER • LV. 12")
                    .setPosition(3.5f, 1.18f, 2.4f).setYaw(0.35f).setHealth(17, 20));
                player.setDynamicColliders(() -> entityWorld.entities().stream()
                    .filter(entity -> !entity.id().equals(localPlayerEntity.id()) && entity.type() != EntityType.ITEM
                        && entity.visible() && entity.modelVisible() && entity.health() > 0)
                    .map(EntityRaycast::hitbox).toList());
                entityWorld.add(new Entity("npc-guide-elyra", EntityType.NPC, guideModel, "Elyra", "GUIDE • ISKUT")
                    .setPosition(-5.5f, 1.18f, -3.5f).setYaw(0.0f).setHealth(20, 20).setShowHealthBar(false));
                Entity dockCaptain = new Entity("npc-dock-captain", EntityType.NPC, guideModel, "Captain Rowan", "HARBOR MASTER")
                    .setPosition(0.0f, 1.18f, 56.5f).setYaw((float) Math.PI).setHealth(20, 20).setShowHealthBar(false);
                entityWorld.add(dockCaptain);
                Entity skyWisp = new Entity("mob-sky-wisp-01", EntityType.MOB, wispModel, "Sky Wisp", "LV. 3 • HOSTILE")
                    .setPosition(-3.5f, 1.38f, 4.0f).setHealth(20, 20).setDefense(4.0f);
                entityWorld.add(skyWisp);
                ItemDefinitionRegistry itemCatalog = ItemDefinitionLoader.loadDirectory(dataDirectory("data/items"));
                ItemVisualRegistry itemVisuals = new ItemVisualRegistry(
                    dataDirectory("data/items/models"), dataDirectory("data/cache/item-icons"), entityModelLoader, itemCatalog);
                HeldItemViewModel heldItemViewModel = new HeldItemViewModel(entityCube, itemVisuals);
                ItemWorldRenderSystem worldItemModels = new ItemWorldRenderSystem(itemVisuals);
                loadingScreen.present(renderer, window, 0.58f, "Preparing characters and items...");
                String playerId = playerId(args);
                JsonFilePlayerDataRepository filePlayerData = new JsonFilePlayerDataRepository(playerDataDirectory(args));
                PlayerDataRepository playerDataRepository = filePlayerData;
                PlayerStats playerStats = new PlayerStats(new EquipmentLoadout());
                InventoryModel inventory;
                long coins;
                Optional<PlayerDataSnapshot> loadedPlayer = playerDataRepository.load(playerId);
                if (loadedPlayer.isPresent()) {
                    PlayerDataSnapshot saved = loadedPlayer.get();
                    inventory = new InventoryModel();
                    playerStats.applyBaseStatsSnapshot(saved.baseStats());
                    inventory.applySnapshot(saved.inventory(), itemCatalog);
                    inventory.syncStats(playerStats, itemCatalog);
                    playerStats.restoreVitals(saved.health(), saved.mana());
                    coins = saved.coins();
                    System.out.println("[Iskut Save] Loaded player '" + playerId + "' from " + filePlayerData.fileFor(playerId));
                } else {
                    inventory = InventoryModel.starterLoadout(itemCatalog);
                    inventory.syncStats(playerStats, itemCatalog);
                    playerStats.respawn();
                    coins = 0L;
                    savePlayerData(playerDataRepository, playerId, inventory, playerStats, coins);
                    System.out.println("[Iskut Save] Created new player '" + playerId + "' at " + filePlayerData.fileFor(playerId));
                }
                loadingScreen.present(renderer, window, 0.76f, "Loading player data...");
                // Inventory is the source of equipped item stats; legacy EquipmentLoadout remains empty.
                System.out.println("[Iskut Items] Loaded " + itemCatalog.size() + " item definitions; inventory slots="
                    + InventoryModel.STORAGE_SIZE + ", hotbar=" + InventoryModel.HOTBAR_SIZE + ", accessory slots=" + InventoryModel.ACCESSORY_SLOTS);
                Map<String, PlayerProfileSnapshot> playerProfiles = Map.of(
                    "player-demo-01", new PlayerProfileSnapshot("player-demo-01", "Astra", 12,
                        17, 20, 44, 60, 14, 22, 9, true,
                        java.util.List.of("gale_saber", "skyguard_helmet", "skyguard_chestplate", "comet_fox")));
                EntityInteractionRegistry entityInteractions = new EntityInteractionRegistry()
                    .register("npc-guide-elyra", "Talk", npc -> npc.displayName()
                        + ": Welcome to Iskut Harbor! Explore the island and watch for hostile creatures beyond the safe paths.")
                    .register("npc-dock-captain", "Open Ship Menu", npc -> "SHIP_MENU");
                HostileMobSystem hostileMobs = new HostileMobSystem();
                hostileMobs.register(skyWisp, 7.5f, 1.25f, 2.15f, 9.0f, 1.35f, 0.34f, 9.0f);
                EntityRenderSystem entityRenderSystem = new EntityRenderSystem();
                Camera camera = new Camera();
                long previousTime = System.nanoTime();
                long lastPlayerSaveNanos = previousTime;
                long statsStart = previousTime;
                int frames = 0;
                SceneRenderStats lastStats = new SceneRenderStats(scene.size(), scene.size(), 0, 0, 0, 0);

                UiRoot hud = new UiRoot();
                UiCrosshair crosshair = new UiCrosshair();
                hud.add(crosshair, UiAnchor.CENTER, 0, 0, 24, 24);

                // Dev telemetry is hidden during normal play; F3 reveals the diagnostics overlay.
                UiPanel debugPanel = new UiPanel(0xE6101720, 0x66405262, 1.0f, 3.0f);
                debugPanel.add(new UiLabel("ISKUT  /  DEVELOPER OVERLAY", 12, 0xFF8DD9EA, true, false),
                    UiAnchor.TOP_LEFT, 12, 8, 300, 20);
                UiLabel fpsLabel = new UiLabel("FPS: --", 13, 0xFFF5F7FA, true, true);
                UiLabel backendLabel = new UiLabel(renderer.description(), 11, 0xFFCCD6E0, false, true);
                UiLabel sceneLabel = new UiLabel("Objects: --", 11, 0xFFCCD6E0, false, true);
                UiLabel drawLabel = new UiLabel("Triangles: --", 11, 0xFFCCD6E0, false, true);
                UiLabel playerLabel = new UiLabel("Player: --", 11, 0xFFCCD6E0, false, true);
                UiLabel entityLabel = new UiLabel("Entities: --", 11, 0xFF8DEBFF, false, true);
                debugPanel.add(backendLabel, UiAnchor.TOP_LEFT, 12, 31, 350, 16);
                debugPanel.add(sceneLabel, UiAnchor.TOP_LEFT, 12, 51, 350, 16);
                debugPanel.add(drawLabel, UiAnchor.TOP_LEFT, 12, 71, 350, 16);
                debugPanel.add(playerLabel, UiAnchor.TOP_LEFT, 12, 91, 350, 16);
                debugPanel.add(entityLabel, UiAnchor.TOP_LEFT, 12, 111, 350, 16);
                hud.add(debugPanel, UiAnchor.TOP_LEFT, 14, 82, 380, 142);

                UiPanel fpsPanel = new UiPanel(0xE6101720, 0x66405262, 1.0f, 3.0f);
                fpsPanel.add(fpsLabel, UiAnchor.CENTER, 0, 0, 224, 22);
                hud.add(fpsPanel, UiAnchor.TOP_RIGHT, 14, 14, 248, 42);
                fpsPanel.setVisible(false);

                // Compact brand/zone header: restrained surfaces, single accent colour, clean alignment.
                UiPanel gameStatusPanel = new UiPanel(0xD9101720, 0x67405262, 1.0f, 3.0f);
                gameStatusPanel.add(new UiPanel(0xFF55C9D5, 0x00000000, 0.0f, 0.0f),
                    UiAnchor.TOP_LEFT, 0, 0, 3, 50);
                UiLabel brandLabel = new UiLabel("ISKUT", 15, 0xFF8DD9EA, true, false);
                UiLabel mapNameLabel = new UiLabel(loadedMap == null ? "DEVELOPER SCENE" : loadedMap.name().toUpperCase(java.util.Locale.ROOT),
                    9, 0xFFB4C0CB, false, false);
                UiLabel shardCountLabel = new UiLabel("SHARDS  0", 11, 0xFFE8C77D, true, false, UiDrawList.TextAlign.RIGHT);
                gameStatusPanel.add(brandLabel, UiAnchor.TOP_LEFT, 13, 6, 120, 20);
                gameStatusPanel.add(mapNameLabel, UiAnchor.TOP_LEFT, 13, 27, 160, 14);
                gameStatusPanel.add(shardCountLabel, UiAnchor.CENTER_RIGHT, 12, 0, 94, 18);
                hud.add(gameStatusPanel, UiAnchor.TOP_LEFT, 14, 14, 300, 50);

                UiPanel targetPanel = new UiPanel(0xD9101720, 0x67405262, 1.0f, 3.0f);
                UiLabel targetLabel = new UiLabel("", 11, 0xFFE8C77D, true, false, UiDrawList.TextAlign.CENTER);
                targetPanel.add(targetLabel, UiAnchor.CENTER, 8, 0, 540, 18);
                targetPanel.setVisible(false);
                hud.add(targetPanel, UiAnchor.BOTTOM_CENTER, 0, 62, 560, 28);

                UiPanel interactionPanel = new UiPanel(0xE6101720, 0x7755C9D5, 1.0f, 3.0f);
                interactionPanel.add(new UiPanel(0xFF55C9D5, 0x00000000, 0.0f, 0.0f),
                    UiAnchor.TOP_LEFT, 0, 0, 3, 30);
                UiLabel interactionLabel = new UiLabel("", 11, 0xFFF0F4F7, true, false, UiDrawList.TextAlign.CENTER);
                interactionPanel.add(interactionLabel, UiAnchor.CENTER, 8, 0, 600, 20);
                interactionPanel.setVisible(false);
                hud.add(interactionPanel, UiAnchor.BOTTOM_CENTER, 0, 96, 620, 30);

                UiPanel playerStatusPanel = new UiPanel(0xD9101720, 0x67405262, 1.0f, 3.0f);
                UiLabel healthCaption = new UiLabel("HEALTH", 9, 0xFFB4C0CB, true, false);
                UiLabel healthLabel = new UiLabel("-- / --", 10, 0xFFF0F4F7, true, false, UiDrawList.TextAlign.RIGHT);
                UiProgressBar healthBar = new UiProgressBar(100, 100, 0xFF303941, 0xFFE76F78);
                UiLabel manaCaption = new UiLabel("MANA", 9, 0xFFB4C0CB, true, false);
                UiLabel manaLabel = new UiLabel("-- / --", 10, 0xFFF0F4F7, true, false, UiDrawList.TextAlign.RIGHT);
                UiProgressBar manaBar = new UiProgressBar(100, 100, 0xFF303941, 0xFF6E9AE8);
                UiLabel combatStatsLabel = new UiLabel("DEF --     STR --     INT --", 9, 0xFFB4C0CB, false, false);
                playerStatusPanel.add(healthCaption, UiAnchor.TOP_LEFT, 12, 7, 90, 12);
                playerStatusPanel.add(healthLabel, UiAnchor.TOP_RIGHT, 12, 7, 120, 12);
                playerStatusPanel.add(healthBar, UiAnchor.TOP_LEFT, 12, 22, 286, 5);
                playerStatusPanel.add(manaCaption, UiAnchor.TOP_LEFT, 12, 32, 90, 12);
                playerStatusPanel.add(manaLabel, UiAnchor.TOP_RIGHT, 12, 32, 120, 12);
                playerStatusPanel.add(manaBar, UiAnchor.TOP_LEFT, 12, 47, 286, 5);
                playerStatusPanel.add(combatStatsLabel, UiAnchor.BOTTOM_LEFT, 12, 7, 286, 12);
                hud.add(playerStatusPanel, UiAnchor.BOTTOM_LEFT, 14, 14, 310, 78);

                UiPanel profilePanel = new UiPanel(0xF00D141D, 0x86546A7C, 1.0f, 4.0f);
                profilePanel.add(new UiPanel(0xFF55C9D5, 0x00000000, 0.0f, 0.0f),
                    UiAnchor.TOP_LEFT, 0, 0, 3, 155);
                UiLabel profileTitle = new UiLabel("PLAYER PROFILE", 14, 0xFF8DD9EA, true, false);
                UiLabel profileSubtitle = new UiLabel("", 11, 0xFFF0F4F7, true, false);
                UiLabel profileVitals = new UiLabel("", 11, 0xFFE8877E, false, false);
                UiLabel profileGear = new UiLabel("", 10, 0xFFB4C0CB, false, false);
                UiLabel profileHint = new UiLabel("RIGHT-CLICK TO CLOSE", 9, 0xFF8493A2, false, false);
                profilePanel.add(profileTitle, UiAnchor.TOP_LEFT, 16, 12, 350, 22);
                profilePanel.add(profileSubtitle, UiAnchor.TOP_LEFT, 16, 40, 350, 18);
                profilePanel.add(profileVitals, UiAnchor.TOP_LEFT, 16, 65, 350, 18);
                profilePanel.add(profileGear, UiAnchor.TOP_LEFT, 16, 91, 370, 38);
                profilePanel.add(profileHint, UiAnchor.BOTTOM_LEFT, 16, 10, 350, 14);
                profilePanel.setVisible(false);
                hud.add(profilePanel, UiAnchor.CENTER, 0, 0, 400, 150);

                // Contextual action prompts replace the old permanently-on tutorial strip.
                boolean showDebugHud = false;
                boolean previousF3Down = false;
                boolean previousLeftDown = false;
                boolean previousRightDown = false;
                boolean previousEDown = false;
                boolean previousInventoryDown = false;
                boolean previousEscapeDown = false;
                boolean previousRDown = false;
                boolean previousF1Down = false;
                InventoryOverlay inventoryUi = new InventoryOverlay();
                inventoryUi.setItemVisuals(itemVisuals);
                long skyShards = inventory.countItem("sky_shard");
                String interactionMessage = "";
                long interactionMessageUntil = 0L;
                boolean shipMenuOpen = false, shipSummoned = false, sailing = false;
                float shipX = 0.0f, shipZ = 63.0f, shipYaw = 0.0f, shipSpeed = 0.0f;
                RenderObject shipHull = null, shipDeck = null, shipSail = null;
                long respawnAtNanos = 0L;
                boolean oceanDeathPending = false;
                double displayedFps = 0.0;
                double displayedFrameMs = 0.0;
                long lastHudUpdate = previousTime;
                int hudFrames = 0;
                float currentFov = BASE_FOV;
                float entityAnimationTime = 0.0f;
                float heldSwingTimer = 0.0f;
                final float heldSwingDuration = 0.30f;

                System.out.println("Controls: WASD move | Space jump | Shift sprint | Ctrl crouch | 1-9 hotbar | R use selected hotbar item | I inventory | LMB attack/select | RMB player profile | E interact | F3 debug | Esc pause/resume");
                System.out.println("RPG stats: HP " + Math.round(playerStats.health()) + "/" + Math.round(playerStats.maxHealth())
                    + " | MANA " + Math.round(playerStats.mana()) + "/" + Math.round(playerStats.maxMana())
                    + " | DEF " + Math.round(playerStats.defense()) + " | STR " + Math.round(playerStats.strength()));
                System.out.println("Scene objects: " + scene.size() + " | Colliders: " + collisionWorld.colliders().size()
                    + " | Frustum culling: " + (culling ? "ON" : "OFF"));
                if (benchmarkCount > 0) {
                    System.out.println("Benchmark scene: " + benchmarkCount + " cuboids. Compare with --no-culling.");
                } else if (voxelDemo) {
                    System.out.println("Voxel demo: generated terrain is enabled only because --voxel was supplied.");
                } else if (debugDemo) {
                    System.out.println("Debug scene: 9 variable-sized cuboids share one reusable mesh.");
                }

                loadingScreen.present(renderer, window, 1.0f, "Ready!");
                GameScreen screen = GameScreen.MAIN_MENU;
                GameScreen settingsReturnScreen = GameScreen.MAIN_MENU;
                RenderQuality settingsQuality = renderer.quality();

                while (!window.shouldClose()) {
                    window.pollEvents();
                    GameScreen screenAtFrameStart = screen;
                    boolean escapeDown = window.isKeyDown(Key.ESCAPE);
                    boolean escapePressed = escapeDown && !previousEscapeDown;
                    previousEscapeDown = escapeDown;
                    boolean f1Down = window.isKeyDown(Key.F1);
                    boolean screenshotPressed = f1Down && !previousF1Down;
                    previousF1Down = f1Down;
                    boolean leftDown = window.isMouseButtonDown(MouseButton.LEFT);
                    boolean leftClicked = leftDown && !previousLeftDown;
                    boolean rightDown = window.isMouseButtonDown(MouseButton.RIGHT);
                    MousePosition cursor = window.cursorPosition();

                    boolean inventoryDown = window.isKeyDown(Key.I);
                    if (screen == GameScreen.PLAYING && inventoryDown && !previousInventoryDown) {
                        if (!inventoryUi.visible()) {
                            inventoryUi.setVisible(true);
                            window.setMouseCaptured(false);
                        } else if (inventoryUi.closeWithCursorRecovery(inventory, itemCatalog)) {
                            inventoryUi.setVisible(false);
                            window.setMouseCaptured(true);
                        }
                    }
                    previousInventoryDown = inventoryDown;
                    if (screen == GameScreen.PLAYING && escapePressed) {
                        if (shipMenuOpen) { shipMenuOpen = false; window.setMouseCaptured(true); }
                        else if (inventoryUi.visible()) {
                            if (inventoryUi.closeWithCursorRecovery(inventory, itemCatalog)) {
                                inventoryUi.setVisible(false);
                                window.setMouseCaptured(true);
                            }
                        } else {
                            screen = GameScreen.PAUSE_MENU;
                            window.setMouseCaptured(false);
                        }
                    }

                    if (screen != GameScreen.PLAYING) {
                        if (escapePressed && screenAtFrameStart != GameScreen.PLAYING) {
                            if (screen == GameScreen.PAUSE_MENU) screen = GameScreen.PLAYING;
                            else if (screen == GameScreen.SETTINGS) screen = settingsReturnScreen;
                        }
                        if (leftClicked) {
                            switch (screen) {
                                case MAIN_MENU -> {
                                    switch (mainMenu.handleClick(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y())) {
                                        case PLAY -> screen = GameScreen.PLAYING;
                                        case SETTINGS -> {
                                            settingsReturnScreen = GameScreen.MAIN_MENU;
                                            screen = GameScreen.SETTINGS;
                                        }
                                        case QUIT -> window.requestClose();
                                        case NONE -> { }
                                    }
                                }
                                case PAUSE_MENU -> {
                                    switch (pauseMenu.handleClick(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y())) {
                                        case RESUME -> screen = GameScreen.PLAYING;
                                        case SETTINGS -> {
                                            settingsReturnScreen = GameScreen.PAUSE_MENU;
                                            screen = GameScreen.SETTINGS;
                                        }
                                        case MAIN_MENU -> {
                                            screen = GameScreen.MAIN_MENU;
                                            inventoryUi.setVisible(false);
                                        }
                                        case NONE -> { }
                                    }
                                }
                                case SETTINGS -> {
                                    switch (settingsMenu.handleClick(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y())) {
                                        case TOGGLE_QUALITY -> {
                                            settingsQuality = settingsQuality == RenderQuality.SMOOTH
                                                ? RenderQuality.FAST : RenderQuality.SMOOTH;
                                            renderer.setQuality(settingsQuality);
                                        }
                                        case BACK -> screen = settingsReturnScreen;
                                        case NONE -> { }
                                    }
                                }
                                case PLAYING -> { }
                            }
                        }

                        if (screen == GameScreen.PLAYING) {
                            window.setMouseCaptured(true);
                            previousLeftDown = leftDown;
                            previousRightDown = rightDown;
                            previousRDown = window.isKeyDown(Key.R);
                            previousEDown = window.isKeyDown(Key.E);
                            previousF3Down = window.isKeyDown(Key.F3);
                            previousTime = System.nanoTime();
                            continue;
                        }
                        window.setMouseCaptured(false);
                        UiDrawList menuUi = switch (screen) {
                            case MAIN_MENU -> mainMenu.build(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y());
                            case PAUSE_MENU -> pauseMenu.build(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y());
                            case SETTINGS -> settingsMenu.build(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y(), settingsQuality);
                            case PLAYING -> UiDrawList.EMPTY;
                        };
                        drawGuiFrame(renderer, menuUi);
                        previousLeftDown = leftDown;
                        previousRightDown = rightDown;
                        previousRDown = window.isKeyDown(Key.R);
                        previousEDown = window.isKeyDown(Key.E);
                        previousF3Down = window.isKeyDown(Key.F3);
                        continue;
                    }

                    boolean inventoryOpen = inventoryUi.visible();
                    inventoryUi.updateHotbarKeys(window, inventory);
                    boolean rDown = window.isKeyDown(Key.R);
                    if (rDown && !previousRDown && !inventoryOpen) {
                        ItemUseResult useResult = ItemUseService.useSelected(inventory, playerStats, itemCatalog);
                        interactionMessage = useResult.message();
                        interactionMessageUntil = System.nanoTime() + 2_400_000_000L;
                    }
                    previousRDown = rDown;
                    boolean f3Down = window.isKeyDown(Key.F3);
                    if (f3Down && !previousF3Down) showDebugHud = !showDebugHud;
                    previousF3Down = f3Down;
                    debugPanel.setVisible(showDebugHud);
                    fpsPanel.setVisible(showDebugHud);

                    long now = System.nanoTime();
                    float deltaSeconds = (now - previousTime) / 1_000_000_000.0f;
                    previousTime = now;
                    heldSwingTimer = Math.max(0.0f, heldSwingTimer - Math.max(0.0f, deltaSeconds));
                    if (sailing) {
                        boolean fwd = window.isKeyDown(Key.W), back = window.isKeyDown(Key.S);
                        boolean turnL = window.isKeyDown(Key.A), turnR = window.isKeyDown(Key.D);
                        shipSpeed = Math.max(-2.5f, Math.min(10.0f, shipSpeed + ((fwd ? 7.0f : 0.0f) - (back ? 5.0f : 0.0f)) * deltaSeconds));
                        shipSpeed *= (float)Math.pow(0.985, deltaSeconds * 60.0);
                        shipYaw += ((turnL ? 1.0f : 0.0f) - (turnR ? 1.0f : 0.0f)) * deltaSeconds * (0.7f + Math.abs(shipSpeed) * 0.04f);
                        shipX += (float)Math.sin(shipYaw) * shipSpeed * deltaSeconds;
                        shipZ -= (float)Math.cos(shipYaw) * shipSpeed * deltaSeconds;
                        float bob = (float)Math.sin(now * 0.0000014) * 0.055f;
                        shipHull.transform().setPosition(shipX, 0.72f + bob, shipZ).setRotation(0, shipYaw, 0);
                        shipDeck.transform().setPosition(shipX, 1.12f + bob, shipZ).setRotation(0, shipYaw, 0);
                        shipSail.transform().setPosition(shipX, 2.8f + bob, shipZ).setRotation(0, shipYaw, 0);
                        player.setInputEnabled(false); player.setVehiclePosition(shipX, 1.2, shipZ, shipYaw);
                    } else { player.setInputEnabled(playerStats.alive() && !inventoryOpen && respawnAtNanos == 0L && !shipMenuOpen); player.setMovementMultiplier(playerStats.movementSpeedMultiplier()); player.update(window, deltaSeconds); }
                    // There is no invisible global floor: if the player falls through the ocean,
                    // the sea becomes a real world hazard and uses the normal death/respawn flow.
                    if (player.belowOceanRespawnPlane() && playerStats.alive()) {
                        float defenseForSeaDamage = Math.max(0.0f, playerStats.defense());
                        float fatalRawDamage = (playerStats.health() + 1.0f)
                            * (100.0f + defenseForSeaDamage) / 100.0f * 1.05f;
                        playerStats.takeDamage(fatalRawDamage);
                        oceanDeathPending = true;
                    }
                    playerStats.tick(deltaSeconds);
                    float fallDamage = player.consumeFallDamage();
                    if (fallDamage > 0 && playerStats.alive()) {
                        float appliedFallDamage = playerStats.takeDamage(fallDamage);
                        interactionMessage = "Fall damage: " + Math.round(appliedFallDamage) + " HP";
                        interactionMessageUntil = System.nanoTime() + 2_500_000_000L;
                    }
                    entityAnimationTime += Math.max(0.0f, Math.min(deltaSeconds, 0.05f));

                    int width = window.framebufferWidth();
                    int height = window.framebufferHeight();
                    if (width <= 0 || height <= 0) {
                        try { Thread.sleep(15L); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                        continue;
                    }

                    float targetFov = player.sprinting() ? SPRINT_FOV : BASE_FOV;
                    float fovBlend = (float) (1.0 - Math.exp(-8.5 * Math.max(0.0, Math.min(deltaSeconds, 0.05))));
                    currentFov += (targetFov - currentFov) * fovBlend;
                    camera.setPerspective(currentFov, 0.05f, viewDistance + 32.0f)
                        .setPosition((float) player.x(), (float) (player.feetY() + player.eyeHeight() + player.cameraBob()), (float) player.z())
                        .setRotation((float) player.yaw(), (float) player.pitch());
                    float[] viewProjection = camera.viewProjection(width, height);

                    boolean rightClicked = rightDown && !previousRightDown;
                    previousLeftDown = leftDown;
                    previousRightDown = rightDown;
                    boolean uiMouseHandled = inventoryUi.handleMouse(window.uiWidth(), window.uiHeight(), cursor.x(), cursor.y(),
                        leftClicked && !shipMenuOpen, rightClicked && !shipMenuOpen, window.isKeyDown(Key.LEFT_SHIFT), inventory, itemCatalog);
                    if (shipMenuOpen && leftClicked) {
                        float sc = Math.max(0.75f, Math.min(1.0f, Math.min(window.uiWidth() / 1280.0f, window.uiHeight() / 720.0f)));
                        float bx = (window.uiWidth() - 340 * sc) * 0.5f, by = window.uiHeight() * 0.5f + 10 * sc;
                        if (cursor.x() >= bx && cursor.x() <= bx + 340 * sc && cursor.y() >= by && cursor.y() <= by + 54 * sc) {
                            if (!shipSummoned) {
                                Material hullMat = new Material("ship-hull", 0.38f, 0.20f, 0.10f);
                                Material deckMat = new Material("ship-deck", 0.62f, 0.42f, 0.22f);
                                Material sailMat = new Material("ship-sail", 0.82f, 0.78f, 0.58f);
                                shipHull = new RenderObject("summoned-ship-hull", entityCube, hullMat, new Transform().setPosition(shipX, 0.72f, shipZ).setScale(4.0f, 0.65f, 1.8f));
                                shipDeck = new RenderObject("summoned-ship-deck", entityCube, deckMat, new Transform().setPosition(shipX, 1.12f, shipZ).setScale(3.2f, 0.18f, 1.45f));
                                shipSail = new RenderObject("summoned-ship-sail", entityCube, sailMat, new Transform().setPosition(shipX, 2.8f, shipZ).setScale(0.12f, 3.0f, 2.0f));
                                scene.add(shipHull); scene.add(shipDeck); scene.add(shipSail);
                                if (renderScene != scene) { renderScene.add(shipHull); renderScene.add(shipDeck); renderScene.add(shipSail); }
                                shipSummoned = true;
                            } else { sailing = !sailing; }
                        } else if (cursor.x() >= (window.uiWidth() - 220 * sc) * 0.5f && cursor.x() <= (window.uiWidth() + 220 * sc) * 0.5f && cursor.y() > window.uiHeight() * 0.5f + 80 * sc) {
                            shipMenuOpen = false; window.setMouseCaptured(true);
                        }
                    }
                    if (uiMouseHandled) inventory.syncStats(playerStats, itemCatalog);
                    if (!inventoryOpen && !uiMouseHandled && leftClicked) heldSwingTimer = heldSwingDuration;
                    double cosPitch = Math.cos(player.pitch());
                    double rayX = Math.sin(player.yaw()) * cosPitch;
                    double rayY = Math.sin(player.pitch());
                    double rayZ = -Math.cos(player.yaw()) * cosPitch;
                    double rayOriginX = player.x();
                    double rayOriginY = player.feetY() + player.eyeHeight() + player.cameraBob();
                    double rayOriginZ = player.z();
                    Optional<EntityRaycast.Hit> entityHit = inventoryOpen ? Optional.empty() : EntityRaycast.raycast(
                        entityWorld, collisionWorld, localPlayerEntity.id(),
                        rayOriginX, rayOriginY, rayOriginZ, rayX, rayY, rayZ, 6.0);
                    Optional<RaycastHit> hit = targeting.update(
                        rayOriginX, rayOriginY, rayOriginZ, rayX, rayY, rayZ,
                        !inventoryOpen && !uiMouseHandled && leftClicked && entityHit.isEmpty(),
                        !inventoryOpen && !uiMouseHandled && rightClicked && entityHit.isEmpty());

                    if (!inventoryOpen && !uiMouseHandled && leftClicked && entityHit.isPresent()) {
                        Entity clicked = entityHit.get().entity();
                        if (clicked.type() == EntityType.MOB) {
                            interactionMessage = hostileMobs.playerAttack(clicked, entityWorld, playerStats);
                            if (interactionMessage.isBlank()) interactionMessage = "You cannot attack that target right now.";
                            interactionMessageUntil = System.nanoTime() + 2_400_000_000L;
                            profilePanel.setVisible(false);
                        } else if (clicked.type() == EntityType.PLAYER) {
                            interactionMessage = "Player target: " + clicked.displayName() + " • Right-click to view profile.";
                            interactionMessageUntil = System.nanoTime() + 2_000_000_000L;
                        }
                    }

                    if (!inventoryOpen && !uiMouseHandled && rightClicked) {
                        if (entityHit.isPresent() && entityHit.get().entity().type() == EntityType.PLAYER) {
                            Entity viewedPlayer = entityHit.get().entity();
                            PlayerProfileSnapshot profile = playerProfiles.get(viewedPlayer.id());
                            if (profile != null) {
                                profileTitle.setText("PLAYER PROFILE  /  " + profile.displayName().toUpperCase(java.util.Locale.ROOT)
                                    + "  •  LV. " + profile.level());
                                profileSubtitle.setText(profile.online() ? "PLAYER  •  ONLINE" : "PLAYER  •  OFFLINE");
                                profileVitals.setText("HP " + Math.round(profile.health()) + "/" + Math.round(profile.maxHealth())
                                    + "   MANA " + Math.round(profile.mana()) + "/" + Math.round(profile.maxMana())
                                    + "   DEF " + Math.round(profile.defense()));
                                profileGear.setText("STR " + Math.round(profile.strength()) + "  |  INT " + Math.round(profile.intelligence())
                                    + "  |  Gear: " + String.join(", ", profile.equippedItemIds().stream().limit(3).toList())
                                    + (profile.equippedItemIds().size() > 3 ? " + more" : ""));
                            } else {
                                profileTitle.setText("PLAYER PROFILE  /  " + viewedPlayer.displayName().toUpperCase(java.util.Locale.ROOT));
                                profileSubtitle.setText(viewedPlayer.subtitle());
                                profileVitals.setText("Health: " + Math.round(viewedPlayer.health()) + "/" + Math.round(viewedPlayer.maxHealth())
                                    + "   |   Status: " + (viewedPlayer.health() > 0 ? "ALIVE" : "DEFEATED"));
                                profileGear.setText("Profile data has not synced from the server yet.");
                            }
                            profilePanel.setVisible(true);
                        } else {
                            profilePanel.setVisible(false);
                        }
                    }

                    boolean eDown = window.isKeyDown(Key.E);
                    boolean interactPressed = eDown && !previousEDown;
                    previousEDown = eDown;
                    if (!inventoryOpen && interactPressed && entityHit.isPresent() && entityHit.get().entity().type() == EntityType.NPC) {
                        Entity npc = entityHit.get().entity();
                        interactionMessage = entityInteractions.interact(npc).orElse("This NPC has no interaction configured yet.");
                        if ("SHIP_MENU".equals(interactionMessage)) { shipMenuOpen = true; interactionMessage = ""; window.setMouseCaptured(false); }
                        interactionMessageUntil = System.nanoTime() + 6_000_000_000L;
                        profilePanel.setVisible(false);
                    } else if (!inventoryOpen && interactPressed && loadedMap != null && targeting.target() != null && entityHit.isEmpty()) {
                        var targetObject = targeting.target();
                        GameMap.Interaction interaction = loadedMap.interactions().get(targetObject.id());
                        if (interaction != null) {
                            switch (interaction.type()) {
                                case COLLECT_CRYSTAL -> {
                                    ItemStack remainder = inventory.add(new ItemStack("sky_shard", 1), itemCatalog);
                                    if (remainder == null && scene.remove(targetObject.id()) != null) {
                                        if (renderScene != scene) renderScene.remove(targetObject.id());
                                        targeting.forget(targetObject);
                                        collisionWorld.rebuild(scene);
                                        skyShards = inventory.countItem("sky_shard");
                                        interactionMessage = "Collected a Sky Shard! Total: " + skyShards;
                                    } else {
                                        interactionMessage = "Inventory full. Make room before collecting this shard.";
                                    }
                                }
                                case PORTAL, NPC, SHOP, INFO -> interactionMessage = interaction.message();
                            }
                            interactionMessageUntil = System.nanoTime() + 3_500_000_000L;
                        }
                    }

                    if (hit.isPresent() && loadedMap != null && targeting.target() == null) hit = Optional.empty();
                    String targetStatus;
                    if (entityHit.isPresent() && entityWorld.get(entityHit.get().entity().id()) != null) {
                        Entity targetEntity = entityHit.get().entity();
                        targetStatus = switch (targetEntity.type()) {
                            case NPC -> targetEntity.displayName() + "  |  " + targetEntity.subtitle() + "  |  [E] "
                                + entityInteractions.definition(targetEntity).map(EntityInteractionRegistry.Definition::prompt).orElse("Interact");
                            case PLAYER -> targetEntity.displayName() + "  |  " + targetEntity.subtitle() + "  |  [RMB] Profile";
                            case MOB -> targetEntity.displayName() + "  |  HP " + Math.round(targetEntity.health()) + "/" + Math.round(targetEntity.maxHealth()) + "  |  [LMB] Attack";
                            case PET -> targetEntity.displayName() + "  |  Companion";
                            case ITEM -> targetEntity.displayName() + "  |  [E] Pick up";
                        };
                    } else {
                        targetStatus = targeting.status(hit);
                    }
                    targetPanel.setVisible(!targetStatus.isBlank());
                    if (!targetStatus.isBlank()) targetLabel.setText(targetStatus);
                    shardCountLabel.setText("SHARDS  " + skyShards);

                    if (now - lastPlayerSaveNanos >= PLAYER_AUTOSAVE_INTERVAL_NANOS && inventory.cursorStack() == null) {
                        try {
                            savePlayerData(playerDataRepository, playerId, inventory, playerStats, coins);
                            lastPlayerSaveNanos = now;
                        } catch (IOException saveFailure) {
                            System.err.println("[Iskut Save] Autosave failed: " + saveFailure.getMessage());
                            lastPlayerSaveNanos = now;
                        }
                    }

                    String prompt = "";
                    long promptNow = System.nanoTime();
                    if (promptNow < interactionMessageUntil) {
                        prompt = interactionMessage;
                    } else if (entityHit.isPresent() && entityWorld.get(entityHit.get().entity().id()) != null) {
                        Entity targetEntity = entityHit.get().entity();
                        if (targetEntity.type() == EntityType.NPC) prompt = "[E] "
                            + entityInteractions.definition(targetEntity).map(definition -> definition.prompt() + " to " + targetEntity.displayName())
                                .orElse("Interact with " + targetEntity.displayName());
                        else if (targetEntity.type() == EntityType.PLAYER) prompt = "[RMB] View " + targetEntity.displayName() + "'s profile";
                        else if (targetEntity.type() == EntityType.MOB) prompt = "[LMB] Attack " + targetEntity.displayName();
                    } else if (loadedMap != null && targeting.target() != null) {
                        GameMap.Interaction interaction = loadedMap.interactions().get(targeting.target().id());
                        if (interaction != null) prompt = "[E] " + interaction.title();
                    }
                    interactionPanel.setVisible(!prompt.isBlank());
                    if (!prompt.isBlank()) interactionLabel.setText(prompt);

                    if (!inventoryOpen) {
                        for (String mobEvent : hostileMobs.update(deltaSeconds, player, playerStats, entityWorld, collisionWorld)) {
                            interactionMessage = mobEvent;
                            interactionMessageUntil = System.nanoTime() + 2_400_000_000L;
                        }
                    }
                    if (entityWorld.get(skyWisp.id()) != null) {
                        skyWisp.setPosition(skyWisp.x(), 1.38f + (float) Math.sin(entityAnimationTime * 2.0f) * 0.12f, skyWisp.z());
                    }
                    now = System.nanoTime();
                    if (!playerStats.alive() && respawnAtNanos == 0L) {
                        respawnAtNanos = now + 2_500_000_000L;
                        player.setInputEnabled(false);
                        interactionMessage = oceanDeathPending ? "Lost at sea. Respawning..." : "You were defeated. Respawning...";
                        interactionMessageUntil = respawnAtNanos;
                    }
                    if (respawnAtNanos > 0L && now >= respawnAtNanos) {
                        playerStats.respawn();
                        player.teleportToSpawn();
                        player.setInputEnabled(true);
                        respawnAtNanos = 0L;
                        oceanDeathPending = false;
                        interactionMessage = "Respawned at the hub.";
                        interactionMessageUntil = now + 2_000_000_000L;
                    }

                    float camX = (float) player.x();
                    float camY = (float) (player.feetY() + player.eyeHeight() + player.cameraBob());
                    float camZ = (float) player.z();
                    // Keep a real entity for the local player in the shared model, but never draw it in first-person.
                    localPlayerEntity.setPosition((float) player.x(), (float) player.feetY(), (float) player.z())
                        .setYaw((float) player.yaw()).setHealth(playerStats.health(), playerStats.maxHealth())
                        .setDefense(playerStats.defense());
                    entityRenderSystem.sync(entityWorld);

                    SkyboxSettings skybox = SkyboxSettings.DAYLIGHT;
                    renderer.beginFrame(new RenderFrame(viewProjection, skybox.horizonRed(), skybox.horizonGreen(), skybox.horizonBlue(),
                        viewDistance * 0.6f, viewDistance, skybox));
                    lastStats = sceneRenderer.render(renderScene, viewProjection, camX, camY, camZ);
                    SceneRenderStats entityStats = entitySceneRenderer.render(entityRenderSystem.scene(), viewProjection, camX, camY, camZ);
                    worldItemModels.update(deltaSeconds);
                    SceneRenderStats worldItemStats = entitySceneRenderer.render(worldItemModels.scene(), viewProjection, camX, camY, camZ);
                    float heldSwing = heldSwingTimer > 0.0f
                        ? (float) Math.sin((1.0 - heldSwingTimer / heldSwingDuration) * Math.PI) : 0.0f;
                    heldItemViewModel.update(inventory.selectedHotbarStack(), itemCatalog,
                        camX, camY, camZ, (float) player.yaw(), (float) player.pitch(),
                        (float) player.cameraBob() * 0.35f, heldSwing);
                    SceneRenderStats viewModelStats = viewModelSceneRenderer.render(heldItemViewModel.scene(), viewProjection, camX, camY, camZ);

                    now = System.nanoTime();
                    if (now - lastHudUpdate >= 250_000_000L) {
                        double elapsed = Math.max(0.001, (now - lastHudUpdate) / 1_000_000_000.0);
                        displayedFps = hudFrames > 0 ? hudFrames / elapsed : 0.0;
                        displayedFrameMs = displayedFps > 0.0 ? 1000.0 / displayedFps : 0.0;
                        fpsLabel.setText(String.format(java.util.Locale.ROOT, "FPS: %.1f   |   %.2f ms/frame", displayedFps, displayedFrameMs));
                        backendLabel.setText("Renderer: " + renderer.description());
                        sceneLabel.setText("Objects: " + lastStats.totalObjects() + "   |   Visible: " + lastStats.visibleObjects()
                            + "   |   Culled: " + lastStats.frustumCulledObjects());
                        drawLabel.setText("Triangles: " + (lastStats.trianglesSubmitted() + entityStats.trianglesSubmitted() + worldItemStats.trianglesSubmitted() + viewModelStats.trianglesSubmitted())
                            + "   |   Draws: " + (lastStats.drawCalls() + entityStats.drawCalls() + worldItemStats.drawCalls() + viewModelStats.drawCalls()));
                        entityLabel.setText("Entities: " + entityWorld.size() + "   |   NPC " + entityWorld.count(EntityType.NPC)
                            + "   MOB " + entityWorld.count(EntityType.MOB) + "   PLAYER " + entityWorld.count(EntityType.PLAYER));
                        healthLabel.setText(Math.round(playerStats.health()) + " / " + Math.round(playerStats.maxHealth()));
                        healthBar.setValue(playerStats.health(), playerStats.maxHealth());
                        manaLabel.setText(Math.round(playerStats.mana()) + " / " + Math.round(playerStats.maxMana()));
                        manaBar.setValue(playerStats.mana(), playerStats.maxMana());
                        combatStatsLabel.setText("DEF " + Math.round(playerStats.defense()) + "     STR "
                            + Math.round(playerStats.strength()) + "     INT " + Math.round(playerStats.intelligence()));
                        String state = player.crouching() ? "CROUCH" : (player.sprinting() ? "SPRINT" : (player.grounded() ? "WALK" : "AIR"));
                        playerLabel.setText(String.format(java.util.Locale.ROOT,
                            "Player: %s  |  %.1f u/s  |  X %.1f  Y %.1f  Z %.1f",
                            state, player.horizontalSpeed(), player.x(), player.feetY(), player.z()));
                        lastHudUpdate = now;
                        hudFrames = 0;
                    }
                    int uiWidth = Math.max(1, window.uiWidth()), uiHeight = Math.max(1, window.uiHeight());
                    // Re-project world-space head anchors every frame so nameplates never trail
                    // behind camera turns or entity movement (Minecraft-style billboard behavior).
                    UiDrawList entityHolograms = EntityHologramRenderer.build(entityWorld, viewProjection,
                        uiWidth, uiHeight, camX, camY, camZ, collisionWorld);
                    UiDrawList.Builder combinedUi = new UiDrawList.Builder()
                        .addAll(hud.build(uiWidth, uiHeight))
                        .addAll(entityHolograms)
                        .addAll(inventoryUi.build(uiWidth, uiHeight, inventory, itemCatalog, playerStats));
                    if (shipMenuOpen) {
                        float sc = Math.max(0.75f, Math.min(1.0f, Math.min(uiWidth / 1280.0f, uiHeight / 720.0f)));
                        float pw = 460 * sc, ph = 290 * sc, px = (uiWidth - pw) * 0.5f, py = (uiHeight - ph) * 0.5f;
                        combinedUi.roundedRect(px, py, pw, ph, 0xF0182430, 12 * sc);
                        combinedUi.strokeRoundedRect(px, py, pw, ph, 0xFF55C9D5, 1.5f * sc, 12 * sc);
                        combinedUi.text(uiWidth * 0.5f, py + 42 * sc, "SHIP MENU", 24 * sc, 0xFF8DEBFF, true, false, UiDrawList.TextAlign.CENTER);
                        combinedUi.text(uiWidth * 0.5f, py + 78 * sc, "Captain Rowan  •  Ocean exploration", 12 * sc, 0xFFB4C0CB, false, false, UiDrawList.TextAlign.CENTER);
                        float bx = (uiWidth - 340 * sc) * 0.5f, by = py + 112 * sc, bh = 54 * sc;
                        boolean hover = cursor.x() >= bx && cursor.x() <= bx + 340 * sc && cursor.y() >= by && cursor.y() <= by + bh;
                        combinedUi.roundedRect(bx, by, 340 * sc, bh, hover ? 0xFF2B6572 : 0xFF203C4B, 8 * sc);
                        combinedUi.strokeRoundedRect(bx, by, 340 * sc, bh, 0xFF8DEBFF, 1.2f * sc, 8 * sc);
                        String buttonText = !shipSummoned ? "SUMMON SHIP" : (!sailing ? "BOARD & SAIL" : "STOP SAILING");
                        combinedUi.text(uiWidth * 0.5f, by + 33 * sc, buttonText, 15 * sc, 0xFFFFFFFF, true, false, UiDrawList.TextAlign.CENTER);
                        combinedUi.text(uiWidth * 0.5f, py + ph - 28 * sc, "W/S throttle  •  A/D steer  •  ESC close", 11 * sc, 0xFFB4C0CB, false, false, UiDrawList.TextAlign.CENTER);
                    }
                    renderer.drawUi(combinedUi.build());
                    if (screenshotPressed) {
                        try {
                            Path screenshot = saveScreenshot(renderer.captureScreenshot());
                            System.out.println("[Iskut] Screenshot saved to " + screenshot.toAbsolutePath());
                        } catch (IOException failure) {
                            System.err.println("[Iskut] Could not save screenshot: " + failure.getMessage());
                        }
                    }
                    renderer.endFrame();
                    frames++;
                    hudFrames++;

                    now = System.nanoTime();
                    if (now - statsStart >= 2_000_000_000L) {
                        double seconds = (now - statsStart) / 1_000_000_000.0;
                        double fps = frames / seconds;
                        System.out.printf(java.util.Locale.ROOT,
                            "[Iskut] %.1f FPS | %.2f ms/frame | objects %d | visible %d | frustum-culled %d | triangles submitted %d | backend draws %d | player %.1f u/s%s%n",
                            fps, 1000.0 / Math.max(0.001, fps), lastStats.totalObjects(),
                            lastStats.visibleObjects(), lastStats.frustumCulledObjects(),
                            lastStats.trianglesSubmitted() + entityStats.trianglesSubmitted() + worldItemStats.trianglesSubmitted() + viewModelStats.trianglesSubmitted(),
                            lastStats.drawCalls() + entityStats.drawCalls() + worldItemStats.drawCalls() + viewModelStats.drawCalls(), player.horizontalSpeed(),
                            player.sprinting() ? " (sprint)" : "");
                        System.out.printf(java.util.Locale.ROOT, "[Iskut RPG] HP %.0f/%.0f | Mana %.0f/%.0f | DEF %.0f | STR %.0f | targetable mobs %d%n",
                            playerStats.health(), playerStats.maxHealth(), playerStats.mana(), playerStats.maxMana(),
                            playerStats.defense(), playerStats.strength(), entityWorld.count(EntityType.MOB));
                        statsStart = now;
                        frames = 0;
                    }
                }
                if (inventory.recoverCursor(itemCatalog)) {
                    inventory.syncStats(playerStats, itemCatalog);
                    savePlayerData(playerDataRepository, playerId, inventory, playerStats, coins);
                    System.out.println("[Iskut Save] Saved player '" + playerId + "' successfully.");
                } else {
                    System.err.println("[Iskut Save] Could not recover the held cursor item; final save skipped to protect existing data.");
                }
            }
        }
    }


    private enum GameScreen { MAIN_MENU, PAUSE_MENU, SETTINGS, PLAYING }

    private static final long PLAYER_AUTOSAVE_INTERVAL_NANOS = 8_000_000_000L;

    private static void drawGuiFrame(Renderer renderer, UiDrawList drawList) {
        SkyboxSettings sky = SkyboxSettings.DAYLIGHT;
        float[] identity = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
        };
        renderer.beginFrame(new RenderFrame(identity, sky.horizonRed(), sky.horizonGreen(), sky.horizonBlue(), 0, 0, sky));
        renderer.drawUi(drawList);
        renderer.endFrame();
    }

    private static Path saveScreenshot(BufferedImage image) throws IOException {
        Path directory = Path.of("screenshots");
        Files.createDirectories(directory);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path file = directory.resolve("screenshot-" + timestamp + ".png");
        int suffix = 1;
        while (Files.exists(file)) {
            file = directory.resolve("screenshot-" + timestamp + "-" + suffix++ + ".png");
        }
        if (!javax.imageio.ImageIO.write(image, "png", file.toFile())) {
            throw new IOException("No PNG writer is available.");
        }
        return file;
    }

    private static String playerId(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--player-id=")) {
                String value = arg.substring("--player-id=".length()).strip();
                if (!value.matches("[A-Za-z0-9_-]{1,64}")) {
                    throw new IllegalArgumentException("--player-id must be 1-64 ASCII letters, digits, '_' or '-'.");
                }
                return value;
            }
        }
        return "local-player";
    }

    private static Path playerDataDirectory(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--save-dir=")) {
                String value = arg.substring("--save-dir=".length()).strip();
                if (value.isEmpty()) throw new IllegalArgumentException("--save-dir cannot be empty.");
                return Path.of(value).toAbsolutePath().normalize();
            }
        }
        Path localRoot = Path.of(".").toAbsolutePath().normalize();
        if (Files.isDirectory(localRoot.resolve("data").resolve("items"))) return localRoot.resolve("saves").resolve("players");
        Path parentRoot = localRoot.resolve("..").normalize();
        if (Files.isDirectory(parentRoot.resolve("data").resolve("items"))) return parentRoot.resolve("saves").resolve("players");
        return localRoot.resolve("saves").resolve("players");
    }

    private static void savePlayerData(PlayerDataRepository repository, String playerId, InventoryModel inventory,
                                       PlayerStats playerStats, long coins) throws IOException {
        if (inventory.cursorStack() != null) throw new IllegalStateException("Refusing to save while an item is held by the inventory cursor.");
        PlayerDataSnapshot snapshot = PlayerDataSnapshot.create(playerId, coins, playerStats.baseStatsSnapshot(),
            playerStats.health(), playerStats.mana(), inventory.snapshot());
        repository.save(snapshot);
    }

    private static Path dataDirectory(String relative) {
        Path path = Path.of(relative);
        if (Files.isDirectory(path)) return path;
        Path parentRelative = Path.of("..").resolve(path).normalize();
        if (Files.isDirectory(parentRelative)) return parentRelative;
        throw new IllegalStateException("Required game data directory was not found: " + relative);
    }

    private static Path dataPath(String relative) {
        Path path = Path.of(relative);
        if (Files.isRegularFile(path)) return path;
        Path parentRelative = Path.of("..").resolve(path).normalize();
        if (Files.isRegularFile(parentRelative)) return parentRelative;
        throw new IllegalStateException("Required game data file was not found: " + relative);
    }

    private static Path mapPath(String[] args) {
        Path requested = null;
        for (String arg : args) {
            if (arg.startsWith("--map=")) {
                String value = arg.substring("--map=".length()).strip();
                if (value.isEmpty()) throw new IllegalArgumentException("Map path cannot be empty. Use --map=data/maps/main_hub.vmap.");
                requested = Path.of(value);
                break;
            }
        }
        if (requested == null) requested = Path.of("data", "maps", "main_hub.vmap");
        if (requested.isAbsolute() || Files.isRegularFile(requested)) return requested;
        // Gradle's :game-client:run task may use the subproject folder as its working directory.
        Path parentRelative = Path.of("..").resolve(requested).normalize();
        if (Files.isRegularFile(parentRelative)) return parentRelative;
        return requested;
    }

    private static void configureRenderScale(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--render-scale=")) continue;
            String value = arg.substring("--render-scale=".length());
            try {
                float scale = Float.parseFloat(value);
                if (!Float.isFinite(scale) || scale < 0.25f || scale > 1.0f) {
                    throw new IllegalArgumentException("Render scale must be between 0.25 and 1.00, e.g. --render-scale=0.5.");
                }
                System.setProperty("skyvoxel.renderScale", Float.toString(scale));
                System.out.printf(java.util.Locale.ROOT, "[Iskut] Software render scale set to %.0f%%\n", scale * 100.0f);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid render scale '" + value + "'. Use --render-scale=0.75.", exception);
            }
            return;
        }
    }

    /** --quality=fast|smooth and --threads=N are forwarded to the backend through system properties. */
    private static void applyRendererArgs(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--quality=")) System.setProperty("skyvoxel.quality", arg.substring("--quality=".length()));
            else if (arg.startsWith("--threads=")) System.setProperty("skyvoxel.threads", arg.substring("--threads=".length()));
        }
    }

    /** --view=N : draw/fog distance in world units (default 64; lower is faster on weak CPUs). */
    private static float viewDistance(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--view=")) continue;
            try {
                float value = Float.parseFloat(arg.substring("--view=".length()));
                if (!Float.isFinite(value) || value < 16.0f || value > 240.0f) {
                    throw new IllegalArgumentException("View distance must be between 16 and 240, e.g. --view=64.");
                }
                return value;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Use --view=64.", exception);
            }
        }
        return 64.0f;
    }

    private static int benchmarkCount(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--benchmark=")) {
                try {
                    int count = Integer.parseInt(arg.substring("--benchmark=".length()));
                    if (count < 1 || count > 5000) throw new IllegalArgumentException("Benchmark count must be between 1 and 5000.");
                    return count;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Use --benchmark=100, --benchmark=500, etc.", exception);
                }
            }
        }
        return 0;
    }

    private static boolean hasArg(String[] args, String expected) {
        for (String arg : args) if (expected.equalsIgnoreCase(arg)) return true;
        return false;
    }
}
