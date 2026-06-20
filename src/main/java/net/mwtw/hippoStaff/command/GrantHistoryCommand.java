package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.grant.GrantAction;
import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.GrantRecord;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GrantHistoryCommand implements CommandExecutor {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final GrantManager grantManager;
    private final MessageService messageService;
    private final Plugin plugin;

    public GrantHistoryCommand(Plugin plugin, GrantManager grantManager, MessageService messageService) {
        this.plugin = plugin;
        this.grantManager = grantManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.grant.history")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length != 1) {
            this.messageService.send(sender, "grant.history-usage");
            return true;
        }

        String targetName = args[0];
        this.grantManager.resolveUniqueId(targetName).thenAccept(uuid -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            List<GrantRecord> records = this.grantManager.history(uuid);
            if (records.isEmpty()) {
                this.messageService.send(sender, "grant.history-empty");
                return;
            }
            Map<String, String> header = new HashMap<>();
            header.put("player", targetName);
            this.messageService.send(sender, "grant.history-header", null, header);

            int count = 0;
            for (GrantRecord record : records) {
                if (count++ >= 20) {
                    break;
                }
                Map<String, String> line = new HashMap<>();
                line.put("time", FORMATTER.format(Instant.ofEpochMilli(record.grantedAtEpochMillis())));
                line.put("group", record.group());
                line.put("actor", record.grantedBy());
                line.put("action", actionLabel(record.action()));
                this.messageService.send(sender, "grant.history-line", null, line);
            }
        })).exceptionally(exception -> {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
            return null;
        });
        return true;
    }

    private String actionLabel(GrantAction action) {
        return switch (action) {
            case GRANTED -> "GRANTED";
            case REVOKED -> "REVOKED";
            case EXPIRED -> "EXPIRED";
        };
    }
}
