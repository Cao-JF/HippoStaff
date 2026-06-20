package net.mwtw.hippoStaff.staffchat;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffChatManager {
    private final Core plugin;
    private final MessageService messageService;
    private final DiscordService discordService;
    private final Set<UUID> toggledPlayers;
    private StaffChatSyncService syncService;

    public StaffChatManager(Core plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.discordService = new DiscordService(plugin);
        this.toggledPlayers = ConcurrentHashMap.newKeySet();
    }

    public void setSyncService(StaffChatSyncService syncService) {
        this.syncService = syncService;
    }

    public boolean isToggled(UUID uuid) {
        return this.toggledPlayers.contains(uuid);
    }

    public boolean toggle(UUID uuid) {
        if (this.toggledPlayers.contains(uuid)) {
            this.toggledPlayers.remove(uuid);
            return false;
        }
        this.toggledPlayers.add(uuid);
        return true;
    }

    public void clear(UUID uuid) {
        this.toggledPlayers.remove(uuid);
    }

    public void initDiscord() {
        this.discordService.start(this::dispatchFromDiscord);
    }

    public void shutdownDiscord() {
        this.discordService.stop();
    }

    public void sendFromPlayer(Player sender, String message, boolean prefixedMessage) {
        String server = this.plugin.getConfig().getString("server-id", "unknown");
        String rank = getPlayerRank(sender.getUniqueId());
        Map<String, String> vars = Map.of("server", server, "player", sender.getName(), "message", message);
        dispatchLocal(sender, server, message);
        this.discordService.sendToDiscord(sender.getName(), sender.getUniqueId(), rank, server, message);
        if (this.syncService != null && this.plugin.getConfig().getBoolean("staff-chat.sync.enabled", true)) {
            String formattedChat = this.messageService.raw("staffchat.format", sender, vars);
            String formattedConsole = this.messageService.raw("staffchat.console", sender, vars);
            this.syncService.publish(sender.getUniqueId(), sender.getName(), server, formattedChat, formattedConsole);
        }
        if (prefixedMessage && this.plugin.getConfig().getBoolean("staff-chat.notify-prefixed", false)) {
            this.messageService.send(sender, "staffchat.sent");
        }
    }

    public void dispatchRemote(UUID senderUuid, String senderName, String server, String formattedChat, String formattedConsole) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!canReceive(recipient)) {
                continue;
            }
            recipient.sendMessage(this.messageService.parse(formattedChat));
        }
        Bukkit.getConsoleSender().sendMessage(this.messageService.parse(formattedConsole));
        // Do NOT call discordService here — the originating server already posted to Discord
    }

    /**
     * Called by the local gateway when a Discord message arrives.
     * Shows in-game and publishes to Redis so servers without a gateway also see it.
     */
    public void dispatchFromDiscord(String username, String content) {
        showDiscordMessage(username, content);
    }

    private void showDiscordMessage(String username, String content) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!canReceive(recipient)) {
                continue;
            }
            this.messageService.send(recipient, "staffchat.discord-format", null, Map.of(
                    "username", username,
                    "message", content
            ));
        }
        Bukkit.getConsoleSender().sendMessage("[SC/Discord] " + username + ": " + content);
    }

    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("hippostaff.staffchat");
    }

    public boolean canReceive(Player player) {
        return player.hasPermission("hippostaff.staffchat.see") || player.hasPermission("hippostaff.staffchat");
    }

    public String getTriggerPrefix() {
        String configured = this.plugin.getConfig().getString("staff-chat.trigger-prefix", "#");
        if (configured == null || configured.isBlank()) {
            return "#";
        }
        return configured;
    }

    private void dispatchLocal(Player sender, String server, String message) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!canReceive(recipient)) {
                continue;
            }
            this.messageService.send(recipient, "staffchat.format", sender, Map.of(
                    "server", server,
                    "player", sender.getName(),
                    "message", message
            ));
        }
        Bukkit.getConsoleSender().sendMessage(
                this.messageService.raw("staffchat.console", sender)
                        .replace("%%server%%", server)
                        .replace("%%player%%", sender.getName())
                        .replace("%%message%%", message)
        );
    }

    private String getPlayerRank(UUID uuid) {
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(uuid);
            if (user == null) {
                return "";
            }
            return user.getPrimaryGroup();
        } catch (Exception ignored) {
            return "";
        }
    }
}
