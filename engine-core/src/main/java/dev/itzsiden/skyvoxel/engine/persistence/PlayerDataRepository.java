package dev.itzsiden.skyvoxel.engine.persistence;

import java.io.IOException;
import java.util.Optional;

/** Storage boundary. Gameplay depends on this interface, not on JSON files, SQL, or a network database. */
public interface PlayerDataRepository {
    Optional<PlayerDataSnapshot> load(String playerId) throws IOException;
    void save(PlayerDataSnapshot snapshot) throws IOException;
}
