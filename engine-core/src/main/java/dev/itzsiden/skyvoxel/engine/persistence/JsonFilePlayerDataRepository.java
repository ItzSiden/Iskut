package dev.itzsiden.skyvoxel.engine.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Local implementation: one versioned JSON aggregate per player, written via temporary file + atomic rename. */
public final class JsonFilePlayerDataRepository implements PlayerDataRepository {
    private final Path directory;

    public JsonFilePlayerDataRepository(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public Path fileFor(String playerId) { return directory.resolve(safePlayerId(playerId) + ".json"); }
    public Path directory() { return directory; }

    @Override public Optional<PlayerDataSnapshot> load(String playerId) throws IOException {
        Path file = fileFor(playerId);
        if (!Files.exists(file)) return Optional.empty();
        try {
            PlayerDataSnapshot data = PlayerDataJsonCodec.decode(Files.readString(file, StandardCharsets.UTF_8));
            if (!data.playerId().equals(playerId)) throw new IOException("Save file playerId does not match requested player ID.");
            return Optional.of(data);
        } catch (RuntimeException | IOException failure) {
            // Never reset or overwrite player data silently. Preserve the exact broken file for recovery.
            throw new IOException("Could not load Iskut player save '" + file + "'. The original file was left untouched. "
                + "Reason: " + failure.getMessage(), failure);
        }
    }

    @Override public void save(PlayerDataSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot");
        Files.createDirectories(directory);
        Path target = fileFor(snapshot.playerId());
        Path temp = directory.resolve(target.getFileName() + ".tmp-" + UUID.randomUUID());
        byte[] bytes = PlayerDataJsonCodec.encode(snapshot).getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(temp, bytes);
            if (Files.exists(target)) {
                Path backup = directory.resolve(target.getFileName() + ".bak");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String safePlayerId(String playerId) {
        if (playerId == null || !playerId.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Player ID must be 1-64 safe ASCII characters.");
        return playerId;
    }
}
