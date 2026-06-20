package net.mwtw.hippoStaff.message;

import net.mwtw.hippoStaff.Core;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMessageManager {
    private final Core plugin;
    private final MessageService messageService;
    private final NetworkPlayerRegistry registry;
    private final Map<UUID, UUID> replyTargets;
    private PrivateMessageSyncService syncService;

    public PrivateMessageManager(Core plugin, MessageService messageService, NetworkPlayerRegistry registry) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.registry = registry;
        this.replyTargets = new ConcurrentHashMap<>();
    }

    public void setSyncService(PrivateMessageSyncService syncService) {
        this.syncService = syncService;
    }

    public void handleJoin(Player player) {
        this.registry.handleJoin(player);
        if (this.syncService != null) {
            this.syncService.publishPresence(player.getUniqueId(), player.getName(), true);
        }
    }

    public void handleQuit(Player player) {
        this.registry.handleQuit(player);
        this.replyTargets.remove(player.getUniqueId());
        if (this.syncService != null) {
            this.syncService.publishPresence(player.getUniqueId(), player.getName(), false);
        }
    }

    public void seedOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.registry.handleJoin(player);
            if (this.syncService != null) {
                this.syncService.publishPresence(player.getUniqueId(), player.getName(), true);
            }
        }
    }

    public void clearReply(UUID uuid) {
        this.replyTargets.remove(uuid);
    }

    public boolean sendMessage(Player sender, String targetName, String message) {
        UUID targetUuid = this.registry.findOnlineByName(targetName);
        if (targetUuid == null) {
            this.messageService.send(sender, "private-message.no-player", sender, Map.of("player", targetName));
            return true;
        }
        if (sender.getUniqueId().equals(targetUuid)) {
            this.messageService.send(sender, "private-message.no-self");
            return true;
        }

        if (deliverLocally(sender.getUniqueId(), sender.getName(), targetUuid, message, false)) {
            return true;
        }

        if (this.syncService == null || !this.plugin.getConfig().getBoolean("private-message.sync.enabled", true)) {
            this.messageService.send(sender, "private-message.no-player", sender, Map.of("player", targetName));
            return true;
        }

        this.syncService.publishMessage(sender.getUniqueId(), sender.getName(), targetUuid, message);
        this.replyTargets.put(sender.getUniqueId(), targetUuid);
        this.messageService.send(sender, "private-message.to", sender, Map.of(
                "player", this.registry.nameOf(targetUuid) == null ? targetName : this.registry.nameOf(targetUuid),
                "message", message
        ));
        return true;
    }

    public boolean reply(Player sender, String message) {
        UUID targetUuid = this.replyTargets.get(sender.getUniqueId());
        if (targetUuid == null) {
            this.messageService.send(sender, "private-message.no-reply");
            return true;
        }
        String targetName = this.registry.nameOf(targetUuid);
        if (targetName == null) {
            this.messageService.send(sender, "private-message.no-player", sender, Map.of("player", "unknown"));
            return true;
        }
        return sendMessage(sender, targetName, message);
    }

    public void handleIncoming(UUID fromUuid, String fromName, UUID toUuid, String message) {
        deliverLocally(fromUuid, fromName, toUuid, message, true);
    }

    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("hippostaff.msg");
    }

    public boolean canReply(CommandSender sender) {
        return sender.hasPermission("hippostaff.msg.reply") || canUse(sender);
    }

    public java.util.List<String> tabCompleteOnlinePlayers(String input) {
        return this.registry.onlineNamesStartingWith(input);
    }

    private boolean deliverLocally(UUID fromUuid, String fromName, UUID toUuid, String message, boolean incomingRemotePacket) {
        Player target = Bukkit.getPlayer(toUuid);
        if (target == null) {
            return false;
        }

        OfflinePlayer senderIdentity = Bukkit.getOfflinePlayer(fromUuid);
        this.messageService.send(target, "private-message.from", senderIdentity, Map.of(
                "player", fromName,
                "message", message
        ));

        Player localSender = Bukkit.getPlayer(fromUuid);
        if (localSender != null) {
            this.messageService.send(localSender, "private-message.to", localSender, Map.of(
                    "player", target.getName(),
                    "message", message
            ));
        }

        this.replyTargets.put(target.getUniqueId(), fromUuid);
        if (!incomingRemotePacket) {
            this.replyTargets.put(fromUuid, target.getUniqueId());
        }
        return true;
    }
}
