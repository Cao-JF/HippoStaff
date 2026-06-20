package net.mwtw.hippoStaff.vanish;

import net.mwtw.hippoStaff.Core;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PickupSettingsManager {
    private final Core plugin;
    private final Map<UUID, Boolean> pickupEnabled;
    private File file;
    private YamlConfiguration data;

    public PickupSettingsManager(Core plugin) {
        this.plugin = plugin;
        this.pickupEnabled = new ConcurrentHashMap<>();
    }

    public void init() {
        this.file = new File(this.plugin.getDataFolder(), "pickup-settings.yml");
        if (!this.file.exists()) {
            this.plugin.saveResource("pickup-settings.yml", false);
        }
        this.data = YamlConfiguration.loadConfiguration(this.file);
        load();
    }

    public boolean isEnabled(UUID uuid) {
        return this.pickupEnabled.getOrDefault(uuid, defaultValue());
    }

    public boolean toggle(UUID uuid) {
        boolean next = !isEnabled(uuid);
        set(uuid, next);
        return next;
    }

    public void set(UUID uuid, boolean enabled) {
        this.pickupEnabled.put(uuid, enabled);
        this.data.set("players." + uuid + ".pickup-enabled", enabled);
        save();
    }

    public void close() {
        save();
    }

    private void load() {
        if (this.data.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : this.data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                boolean value = this.data.getBoolean("players." + key + ".pickup-enabled", defaultValue());
                this.pickupEnabled.put(uuid, value);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean defaultValue() {
        return this.plugin.getConfig().getBoolean("vanish.smart-pickup.default-enabled", false);
    }

    private void save() {
        try {
            this.data.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save pickup-settings.yml: " + exception.getMessage());
        }
    }
}
