package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.screenshare.ScreenShareManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScreenShareCommand implements CommandExecutor, TabCompleter {
    private final ScreenShareManager screenShareManager;
    private final MessageService messageService;

    public ScreenShareCommand(ScreenShareManager screenShareManager, MessageService messageService) {
        this.screenShareManager = screenShareManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.ss")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }
        if (args.length != 1) {
            this.messageService.send(sender, "screenshare.usage");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            this.messageService.send(sender, "errors.player-not-found");
            return true;
        }

        // Exempt players (admins) cannot be frozen, but an already-frozen target can still
        // be released in case the exempt permission was granted after the freeze.
        if (!this.screenShareManager.isFrozen(target.getUniqueId()) && target.hasPermission("hippostaff.ss.exempt")) {
            this.messageService.send(sender, "screenshare.exempt", null, Map.of("player", target.getName()));
            return true;
        }

        this.screenShareManager.toggle(sender, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(online.getName());
                }
            }
            return completions;
        }
        return List.of();
    }
}
