package net.mwtw.hippoStaff.hook;

import net.mwtw.hippoStaff.Core;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class HookManager {
    private final Core plugin;
    private boolean placeholderApiAvailable;
    private boolean simpleVoiceChatAvailable;

    public HookManager(Core plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        this.placeholderApiAvailable = isPluginEnabled("PlaceholderAPI");
        boolean packetEventsAvailable = isPluginEnabled("packetevents");
        this.simpleVoiceChatAvailable = isPluginEnabled("voicechat") || isPluginEnabled("SimpleVoiceChat");

        if (this.placeholderApiAvailable) {
            this.plugin.getLogger().info("Hooked into PlaceholderAPI.");
        } else {
            this.plugin.getLogger().info("PlaceholderAPI was not found. Placeholders are disabled.");
        }

        if (packetEventsAvailable) {
            this.plugin.getLogger().info("PacketEvents detected and ready for API extensions.");
        } else {
            this.plugin.getLogger().info("PacketEvents was not found. Continuing without packet hooks.");
        }

        if (this.simpleVoiceChatAvailable) {
            this.plugin.getLogger().info("Simple Voice Chat detected. Soft support is active.");
        } else {
            this.plugin.getLogger().info("Simple Voice Chat was not found. Continuing without voice integration.");
        }
    }

    public boolean isPlaceholderApiAvailable() {
        return this.placeholderApiAvailable;
    }

    public boolean isSimpleVoiceChatAvailable() {
        return this.simpleVoiceChatAvailable;
    }

    private boolean isPluginEnabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
}
