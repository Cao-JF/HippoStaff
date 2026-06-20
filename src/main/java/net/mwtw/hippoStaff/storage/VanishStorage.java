package net.mwtw.hippoStaff.storage;

import java.util.Map;
import java.util.UUID;

public interface VanishStorage {
    void init() throws Exception;

    boolean get(UUID uuid);

    Map<UUID, Boolean> getAll();

    void set(UUID uuid, boolean vanished);

    void close();
}
