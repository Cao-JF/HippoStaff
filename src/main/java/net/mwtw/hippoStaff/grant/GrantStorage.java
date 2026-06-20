package net.mwtw.hippoStaff.grant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GrantStorage {
    void init() throws Exception;

    void close();

    Map<UUID, List<GrantRecord>> loadActiveGrants();

    Map<UUID, List<GrantRecord>> loadHistory();

    void save(Map<UUID, List<GrantRecord>> active, Map<UUID, List<GrantRecord>> history);

    /** Write a single history event immediately. No-op for YAML storage. */
    default void appendHistory(GrantRecord record) {}
}
