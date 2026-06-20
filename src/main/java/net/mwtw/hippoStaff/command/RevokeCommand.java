package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.gui.GrantGuiService;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class RevokeCommand implements CommandExecutor {
    private final GrantManager grantManager;
    private final GrantGuiService grantGuiService;
    private final MessageService messageService;
    private final Plugin plugin;

    public RevokeCommand(Plugin plugin, GrantManager grantManager, GrantGuiService grantGuiService, MessageService messageService) {
        this.plugin = plugin;
        this.grantManager = grantManager;
        this.grantGuiService = grantGuiService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.grant.revoke")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length == 1 && sender instanceof Player player) {
            String targetName = args[0];
            this.grantManager.resolveUniqueId(targetName).thenAccept(uuid ->
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.grantGuiService.openRevokeMenu(player, uuid, targetName))
            ).exceptionally(exception -> {
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
                return null;
            });
            return true;
        }
        if (args.length < 2) {
            this.messageService.send(sender, "grant.revoke-usage");
            return true;
        }

        String targetName = args[0];
        String group = args[1];
        this.grantManager.resolveUniqueId(targetName).thenCompose(uuid ->
                this.grantManager.revoke(targetName, uuid, group, sender.getName())
                        .thenRun(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                            Map<String, String> replacements = new HashMap<>();
                            replacements.put("player", targetName);
                            replacements.put("group", group);
                            this.messageService.send(sender, "grant.revoked", null, replacements);
                        }))
        ).exceptionally(exception -> {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
            return null;
        });
        return true;
    }
}
