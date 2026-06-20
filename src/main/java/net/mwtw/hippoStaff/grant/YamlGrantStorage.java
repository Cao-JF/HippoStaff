package net.mwtw.hippoStaff.grant;

import net.mwtw.hippoStaff.Core;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class YamlGrantStorage implements GrantStorage {
    private final Core plugin;
    private File file;
    private YamlConfiguration yaml;

    public YamlGrantStorage(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        this.file = new File(this.plugin.getDataFolder(), "grants.yml");
        if (!this.file.exists()) {
            try {
                this.file.getParentFile().mkdirs();
                this.file.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to create grants.yml", exception);
            }
        }
        this.yaml = YamlConfiguration.loadConfiguration(this.file);
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public Map<UUID, List<GrantRecord>> loadActiveGrants() {
        return loadSection("active");
    }

    @Override
    public Map<UUID, List<GrantRecord>> loadHistory() {
        return loadSection("history");
    }

    @Override
    public void save(Map<UUID, List<GrantRecord>> active, Map<UUID, List<GrantRecord>> history) {
        this.yaml.set("active", null);
        this.yaml.set("history", null);
        saveSection("active", active);
        saveSection("history", history);
        try {
            this.yaml.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save grants.yml: " + exception.getMessage());
        }
    }

    private Map<UUID, List<GrantRecord>> loadSection(String root) {
        ConfigurationSection section = this.yaml.getConfigurationSection(root);
        if (section == null) {
            return new HashMap<>();
        }

        Map<UUID, List<GrantRecord>> output = new HashMap<>();
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            List<Map<?, ?>> rawList = section.getMapList(key);
            List<GrantRecord> records = new ArrayList<>();
            for (Map<?, ?> raw : rawList) {
                GrantRecord record = readRecord(raw);
                if (record != null) {
                    records.add(record);
                }
            }
            output.put(uuid, records);
        }
        return output;
    }

    private void saveSection(String root, Map<UUID, List<GrantRecord>> recordsByPlayer) {
        for (Map.Entry<UUID, List<GrantRecord>> entry : recordsByPlayer.entrySet()) {
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (GrantRecord record : entry.getValue()) {
                serialized.add(record.toMap());
            }
            this.yaml.set(root + "." + entry.getKey(), serialized);
        }
    }

    private GrantRecord readRecord(Map<?, ?> raw) {
        Object uuidObj = raw.get("targetUuid");
        Object targetNameObj = raw.get("targetName");
        Object groupObj = raw.get("group");
        Object grantedByObj = raw.get("grantedBy");
        Object reasonObj = raw.get("reason");
        Object grantedAtObj = raw.get("grantedAt");
        Object expiresAtObj = raw.get("expiresAt");
        Object actionObj = raw.get("action");
        if (!(uuidObj instanceof String uuidString) ||
                !(targetNameObj instanceof String targetName) ||
                !(groupObj instanceof String group) ||
                !(grantedByObj instanceof String grantedBy) ||
                !(reasonObj instanceof String reason) ||
                !(grantedAtObj instanceof Number grantedAtNumber) ||
                !(actionObj instanceof String actionName)) {
            return null;
        }

        UUID targetUuid;
        try {
            targetUuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        GrantAction action;
        try {
            action = GrantAction.valueOf(actionName);
        } catch (IllegalArgumentException exception) {
            return null;
        }

        Long expiresAt = expiresAtObj instanceof Number value ? value.longValue() : null;
        return new GrantRecord(
                targetUuid,
                targetName,
                group,
                grantedBy,
                reason,
                grantedAtNumber.longValue(),
                expiresAt,
                action
        );
    }
}
