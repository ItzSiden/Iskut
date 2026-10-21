package dev.itzsiden.skyvoxel.client.items;

import dev.itzsiden.skyvoxel.client.entity.EntityModelLoader;
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

/** One .vmodel per item ID. Provides the shared model plus a cached, automatically-generated inventory icon. */
public final class ItemVisualRegistry {
    private static final String CACHE_SIGNATURE_PREFIX = "iskut-item-icon/" + ItemIconGenerator.GENERATOR_VERSION + "\n";
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
        try (Stream<Path> paths = Files.list(this.modelsRoot)) {
            for (Path modelPath : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vmodel"))
                    .sorted().toList()) {
                String fileName = modelPath.getFileName().toString();
                String itemId = fileName.substring(0, fileName.length() - ".vmodel".length()).toLowerCase(Locale.ROOT);
                if (catalog.find(itemId).isEmpty()) {
                    System.err.println("[Iskut Items] Ignoring model with no matching item definition: " + modelPath);
                    continue;
                }
                try {
                    EntityModel model = loader.load(modelPath);
                    models.put(itemId, model);
                    BufferedImage icon = loadOrGenerateIcon(itemId, modelPath, model);
                    icons.put(itemId, TextureData.fromImage("item-icon/" + itemId, icon));
                } catch (IOException | RuntimeException ex) {
                    System.err.println("[Iskut Items] Could not load item visual '" + itemId + "': " + ex.getMessage());
                }
            }
        }
        System.out.println("[Iskut Items] Loaded " + models.size() + " item models; generated/cached " + icons.size() + " thumbnails from .vmodel assets.");
    }

    public Optional<EntityModel> model(String itemId) { return Optional.ofNullable(models.get(normalize(itemId))); }
    public Optional<TextureData> icon(String itemId) { return Optional.ofNullable(icons.get(normalize(itemId))); }
    public boolean hasModel(String itemId) { return models.containsKey(normalize(itemId)); }
    public int modelCount() { return models.size(); }
    public int iconCount() { return icons.size(); }
    public Path modelsRoot() { return modelsRoot; }

    private BufferedImage loadOrGenerateIcon(String itemId, Path modelPath, EntityModel model) throws IOException {
        long modified = Files.getLastModifiedTime(modelPath).toMillis();
        long size = Files.size(modelPath);
        String signature = CACHE_SIGNATURE_PREFIX + modified + "\n" + size + "\n" + sha256(modelPath) + "\n";
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
