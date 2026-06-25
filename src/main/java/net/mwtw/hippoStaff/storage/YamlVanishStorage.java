package net.mwtw.hippoStaff.storage;

import net.mwtw.hippoStaff.Core;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class YamlVanishStorage implements VanishStorage {
    private final Core plugin;
    private File file;
    private YamlConfiguration configuration;

    public YamlVanishStorage(Core plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized void init() {
        this.file = new File(this.plugin.getDataFolder(), "vanish-data.yml");
        if (!this.file.exists()) {
            this.plugin.saveResource("vanish-data.yml", false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(this.file);
    }

    @Override
    public synchronized boolean get(UUID uuid) {
        return this.configuration.getBoolean("players." + uuid, false);
    }

    @Override
    public synchronized Map<UUID, Boolean> getAll() {
        Map<UUID, Boolean> values = new HashMap<>();
        if (this.configuration.getConfigurationSection("players") == null) {
            return values;
        }
        for (String key : this.configuration.getConfigurationSection("players").getKeys(false)) {
            values.put(UUID.fromString(key), this.configuration.getBoolean("players." + key, false));
        }
        return values;
    }

    @Override
    public synchronized void set(UUID uuid, boolean vanished) {
        this.configuration.set("players." + uuid, vanished);
        save();
    }

    @Override
    public synchronized void close() {
        save();
    }

    private void save() {
        try {
            this.configuration.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save vanish-data.yml: " + exception.getMessage());
        }
    }
}
