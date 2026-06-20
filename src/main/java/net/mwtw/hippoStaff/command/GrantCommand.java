package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.grant.DurationParser;
import net.mwtw.hippoStaff.grant.GrantManager;
import net.mwtw.hippoStaff.grant.gui.GrantGuiService;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class GrantCommand implements CommandExecutor, TabCompleter {
    private final GrantManager grantManager;
    private final GrantGuiService grantGuiService;
    private final MessageService messageService;
    private final Plugin plugin;

    public GrantCommand(Plugin plugin, GrantManager grantManager, GrantGuiService grantGuiService, MessageService messageService) {
        this.plugin = plugin;
        this.grantManager = grantManager;
        this.grantGuiService = grantGuiService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.grant")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length == 1 && sender instanceof Player player) {
            String targetName = args[0];
            this.grantManager.resolveUniqueId(targetName).thenAccept(uuid ->
                    this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.grantGuiService.openMainMenu(player, uuid, targetName))
            ).exceptionally(exception -> {
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
                return null;
            });
            return true;
        }
        if (args.length < 3) {
            this.messageService.send(sender, "grant.usage");
            List<String> groups = this.grantManager.getGrantableGroups();
            Map<String, String> replacements = Map.of("groups", groups.isEmpty() ? "none" : String.join(", ", groups));
            this.messageService.send(sender, "grant.available-groups", null, replacements);
            return true;
        }

        String targetName = args[0];
        String group = args[1].toLowerCase(Locale.ROOT);
        Duration duration;
        try {
            duration = DurationParser.parse(args[2]);
        } catch (IllegalArgumentException exception) {
            this.messageService.send(sender, "grant.invalid-duration");
            return true;
        }
        String reason = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "No reason provided";

        List<String> grantableGroups = this.grantManager.getGrantableGroups();
        if (!grantableGroups.contains(group)) {
            Map<String, String> replacements = new HashMap<>();
            replacements.put("group", group);
            this.messageService.send(sender, "grant.group-not-grantable", null, replacements);
            return true;
        }

        this.grantManager.resolveUniqueId(targetName).thenCompose(uuid ->
                this.grantManager.grant(targetName, uuid, group, sender.getName(), duration, reason)
                        .thenRun(() -> this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                            Map<String, String> replacements = new HashMap<>();
                            replacements.put("player", targetName);
                            replacements.put("group", group);
                            replacements.put("duration", duration == null ? "permanent" : this.grantManager.formatDuration(duration));
                            this.messageService.send(sender, "grant.success", null, replacements);
                        }))
        ).exceptionally(exception -> {
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.messageService.send(sender, "grant.player-not-found"));
            return null;
        });

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.grant")) {
            return List.of();
        }
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (Player player : this.plugin.getServer().getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(name);
                }
            }
            return completions;
        }
        if (args.length == 2) {
            String current = args[1].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (String group : this.grantManager.getGrantableGroups()) {
                if (group.startsWith(current)) {
                    completions.add(group);
                }
            }
            return completions;
        }
        if (args.length == 3) {
            String current = args[2].toLowerCase(Locale.ROOT);
            List<String> presets = List.of("1h", "12h", "1d", "7d", "30d", "perm");
            List<String> completions = new ArrayList<>();
            for (String preset : presets) {
                if (preset.startsWith(current)) {
                    completions.add(preset);
                }
            }
            return completions;
        }
        return List.of();
    }
}
