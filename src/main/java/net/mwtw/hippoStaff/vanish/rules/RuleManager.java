package net.mwtw.hippoStaff.vanish.rules;

import net.mwtw.hippoStaff.Core;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuleManager {
    private final Core plugin;
    private final Map<UUID, PendingChatConfirm> pendingChatConfirmations;

    public RuleManager(Core plugin) {
        this.plugin = plugin;
        this.pendingChatConfirmations = new ConcurrentHashMap<>();
    }

    public void init() {
    }

    public boolean isEnabled(Player player, VanishRule rule) {
        return defaultValue(rule);
    }

    public boolean confirmChat(UUID uuid, String message) {
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(3L, this.plugin.getConfig().getLong("vanish.chat-confirmation.window-seconds", 8L)) * 1000L;
        PendingChatConfirm pending = this.pendingChatConfirmations.get(uuid);
        if (pending != null && pending.message().equals(message) && now <= pending.expiresAt()) {
            this.pendingChatConfirmations.remove(uuid);
            return true;
        }
        this.pendingChatConfirmations.put(uuid, new PendingChatConfirm(message, now + windowMillis));
        return false;
    }

    public void clearChatConfirm(UUID uuid) {
        this.pendingChatConfirmations.remove(uuid);
    }

    public void close() {
    }

    private boolean defaultValue(VanishRule rule) {
        return this.plugin.getConfig().getBoolean("vanish.rules." + rule.key(), false);
    }

    private record PendingChatConfirm(String message, long expiresAt) {
    }
}
