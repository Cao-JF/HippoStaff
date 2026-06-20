package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HippoStaffCommand implements CommandExecutor, TabCompleter {
    private final Core plugin;
    private final MessageService messageService;

    public HippoStaffCommand(Core plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("hippostaff.reload")) {
                this.messageService.send(sender, "errors.no-permission");
                return true;
            }
            this.plugin.reloadHippoStaff(sender);
            return true;
        }

        this.messageService.send(sender, "hippostaff.usage");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (!sender.hasPermission("hippostaff.reload")) {
            return List.of();
        }

        String current = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if ("reload".startsWith(current)) {
            completions.add("reload");
        }
        return completions;
    }
}
