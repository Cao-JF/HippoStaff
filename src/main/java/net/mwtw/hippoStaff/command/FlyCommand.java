package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
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

public final class FlyCommand implements CommandExecutor, TabCompleter {
    private final MessageService messageService;

    public FlyCommand(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.fly")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }

        Boolean state = null;
        int playerArgIndex = 0;

        if (args.length >= 1) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("on")) {
                state = true;
                playerArgIndex = 1;
            } else if (first.equals("off")) {
                state = false;
                playerArgIndex = 1;
            }
        }

        Player target;
        if (args.length > playerArgIndex) {
            if (!sender.hasPermission("hippostaff.fly.others")) {
                this.messageService.send(sender, "errors.no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[playerArgIndex]);
            if (target == null) {
                this.messageService.send(sender, "errors.player-not-found");
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                this.messageService.send(sender, "errors.player-only");
                return true;
            }
            target = player;
        }

        boolean enable = state != null ? state : !target.getAllowFlight();
        target.setAllowFlight(enable);
        if (!enable) {
            target.setFlying(false);
        }

        String msgKey = enable ? "fly.enabled" : "fly.disabled";
        if (sender.equals(target)) {
            this.messageService.send(sender, msgKey);
        } else {
            this.messageService.send(sender, msgKey + "-other", null, Map.of("player", target.getName()));
            this.messageService.send(target, msgKey);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            if ("on".startsWith(current)) completions.add("on");
            if ("off".startsWith(current)) completions.add("off");
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                    completions.add(online.getName());
                }
            }
            return completions;
        }
        if (args.length == 2) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("on") || first.equals("off")) {
                String current = args[1].toLowerCase(Locale.ROOT);
                List<String> completions = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                        completions.add(online.getName());
                    }
                }
                return completions;
            }
        }
        return List.of();
    }
}
