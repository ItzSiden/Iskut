package dev.itzsiden.skyvoxel.client.items;

import dev.itzsiden.skyvoxel.client.entity.EntityModelLoader;
import dev.itzsiden.skyvoxel.client.entity.JavaBlockModelLoader;
import dev.itzsiden.skyvoxel.engine.entity.EntityModel;
import dev.itzsiden.skyvoxel.engine.items.ItemDefinitionRegistry;
import dev.itzsiden.skyvoxel.render.api.TextureData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** One Blockbench Java JSON model per item ID (with legacy .vmodel fallback); caches model-driven inventory icons. */
public final class ItemVisualRegistry {
    private static final String CACHE_SIGNATURE_PREFIX = "iskut-item-icon/" + ItemIconGenerator.GENERATOR_VERSION + "/java-blockbench-v1\n";
    private final Path modelsRoot;
    private final Path iconCacheRoot;
    private final Map<String, EntityModel> models = new LinkedHashMap<>();
    private final Map<String, TextureData> icons = new LinkedHashMap<>();

    public ItemVisualRegistry(Path modelsRoot, Path iconCacheRoot, EntityModelLoader loader,
                              ItemDefinitionRegistry catalog) throws IOException {
        this.modelsRoot = modelsRoot.toAbsolutePath().normalize();
        this.iconCacheRoot = iconCacheRoot.toAbsolutePath().normalize();
        Files.createDirectories(this.modelsRoot);
        Files.createDirectories(this.iconCacheRoot);
        Path itemTextureRoot = this.modelsRoot.getParent().resolve("textures").normalize();
        Path sharedWorldTextures = this.modelsRoot.getParent().getParent().resolve("textures").resolve("world").normalize();
        JavaBlockModelLoader javaLoader = new JavaBlockModelLoader();
        Map<String, Path> candidates = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.list(this.modelsRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String name = path.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                String extension = lower.endsWith(".json") ? ".json" : lower.endsWith(".vmodel") ? ".vmodel" : null;
                if (extension == null) continue;
                String itemId = name.substring(0, name.length() - extension.length()).toLowerCase(Locale.ROOT);
                if (catalog.find(itemId).isEmpty()) {
                    System.err.println("[Iskut Items] Ignoring model with no matching item definition: " + path);
                    continue;
                }
                Path existing = candidates.get(itemId);
                // A Blockbench Java model is authoritative when both the new and legacy asset exist.
                if (existing == null || extension.equals(".json")) candidates.put(itemId, path);
            }
        }
        String textureFingerprint = fingerprintTextures(itemTextureRoot);
        for (Map.Entry<String, Path> entry : candidates.entrySet()) {
            String itemId = entry.getKey();
            Path modelPath = entry.getValue();
            try {
                EntityModel model = modelPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")
                    ? javaLoader.load(modelPath, itemTextureRoot, sharedWorldTextures)
                    : loader.load(modelPath);
                models.put(itemId, model);
                BufferedImage icon = loadOrGenerateIcon(itemId, modelPath, model, textureFingerprint);
                icons.put(itemId, TextureData.fromImage("item-icon/" + itemId, icon));
            } catch (IOException | RuntimeException ex) {
                System.err.println("[Iskut Items] Could not load item visual '" + itemId + "' from " + modelPath.getFileName() + ": " + ex.getMessage());
            }
        }
        System.out.println("[Iskut Items] Loaded " + models.size() + " item models (Blockbench Java JSON preferred; legacy .vmodel fallback); generated/cached " + icons.size() + " thumbnails.");
    }

    public Optional<EntityModel> model(String itemId) { return Optional.ofNullable(models.get(normalize(itemId))); }
    public Optional<TextureData> icon(String itemId) { return Optional.ofNullable(icons.get(normalize(itemId))); }
    public boolean hasModel(String itemId) { return models.containsKey(normalize(itemId)); }
    public int modelCount() { return models.size(); }
    public int iconCount() { return icons.size(); }
    public Path modelsRoot() { return modelsRoot; }

    private BufferedImage loadOrGenerateIcon(String itemId, Path modelPath, EntityModel model, String textureFingerprint) throws IOException {
        long modified = Files.getLastModifiedTime(modelPath).toMillis();
        long size = Files.size(modelPath);
        String signature = CACHE_SIGNATURE_PREFIX + modified + "\n" + size + "\n" + sha256(modelPath) + "\n" + textureFingerprint + "\n";
        Path png = iconCacheRoot.resolve(itemId + ".png");
        Path meta = iconCacheRoot.resolve(itemId + ".sig");
        if (Files.isRegularFile(png) && Files.isRegularFile(meta)) {
            try {
                if (Files.readString(meta, StandardCharsets.UTF_8).equals(signature)) {
                    BufferedImage cached = ImageIO.read(png.toFile());
                    if (cached != null && cached.getWidth() == ItemIconGenerator.ICON_SIZE
                            && cached.getHeight() == ItemIconGenerator.ICON_SIZE) return cached;
                }
            } catch (IOException ignored) { /* Corrupt/stale cache is regenerated below. */ }
        }
        BufferedImage generated = ItemIconGenerator.render(model);
        Path tempPng = png.resolveSibling(png.getFileName() + ".tmp");
        Path tempMeta = meta.resolveSibling(meta.getFileName() + ".tmp");
        if (!ImageIO.write(generated, "png", tempPng.toFile())) throw new IOException("PNG writer is unavailable.");
        Files.writeString(tempMeta, signature, StandardCharsets.UTF_8);
        replaceAtomically(tempPng, png);
        replaceAtomically(tempMeta, meta);
        return generated;
    }

    private static String fingerprintTextures(Path root) throws IOException {
        if (!Files.isDirectory(root)) return "missing-texture-root";
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible); }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList()) {
                digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(Files.size(path)).getBytes(StandardCharsets.UTF_8));
                digest.update(Long.toString(Files.getLastModifiedTime(path).toMillis()).getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(Path path) throws IOException {
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is required by the Java runtime.", impossible); }
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(path)) {
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void replaceAtomically(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException atomicUnsupported) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String normalize(String id) { return id == null ? "" : id.strip().toLowerCase(Locale.ROOT); }
}
