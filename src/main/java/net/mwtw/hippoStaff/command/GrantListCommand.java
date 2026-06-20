package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.GrantRecord;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GrantListCommand implements CommandExecutor {
    private final GrantManager grantManager;
    private final MessageService messageService;
    private final Plugin plugin;

    public GrantListCommand(Plugin plugin, GrantManager grantManager, MessageService messageService) {
        this.plugin = plugin;
        this.grantManager = grantManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.grant.list")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length != 1) {
            this.messageService.send(sender, "grant.list-usage");
            return true;
        }

        String targetName = args[0];
        this.grantManager.resolveUniqueId(targetName).thenAccept(uuid -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            List<GrantRecord> active = this.grantManager.active(uuid);
            if (active.isEmpty()) {
                this.messageService.send(sender, "grant.list-empty");
                return;
            }
            this.messageService.send(sender, "grant.list-header", null, Map.of("player", targetName));
            for (GrantRecord record : active) {
                String remaining = "permanent";
                if (record.expiresAtEpochMillis() != null) {
                    Duration duration = Duration.ofMillis(Math.max(0L, record.expiresAtEpochMillis() - Instant.now().toEpochMilli()));
                    remaining = this.grantManager.formatDuration(duration);
                }
                Map<String, String> replacements = new HashMap<>();
                replacements.put("group", record.group());
                replacements.put("remaining", remaining);
                replacements.put("actor", record.grantedBy());
                this.messageService.send(sender, "grant.list-line", null, replacements);
            }
        })).exceptionally(exception -> {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
            return null;
        });
        return true;
    }
}
