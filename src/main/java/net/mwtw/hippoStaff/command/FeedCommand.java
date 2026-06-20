package net.mwtw.hippoStaff.command;

import net.kyori.adventure.text.Component;
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

public final class FeedCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.feed")) {
            sender.sendMessage(Component.translatable("commands.generic.permission"));
            return true;
        }

        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("hippostaff.feed.others")) {
                sender.sendMessage(Component.translatable("commands.generic.permission"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.translatable("argument.player.notfound"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console must specify a player."));
                return true;
            }
            target = player;
        }

        target.setFoodLevel(20);
        target.setSaturation(20.0f);
        target.setExhaustion(0.0f);

        if (sender.equals(target)) {
            sender.sendMessage(Component.text("You have been fed."));
        } else {
            sender.sendMessage(Component.text("Fed " + target.getName() + "."));
            target.sendMessage(Component.text("You have been fed."));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String current = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                completions.add(online.getName());
            }
        }
        return completions;
    }
}
