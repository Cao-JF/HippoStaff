package net.mwtw.hippoStaff.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RepairCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.repair")) {
            sender.sendMessage(Component.translatable("commands.generic.permission"));
            return true;
        }

        boolean all = args.length >= 1 && args[0].equalsIgnoreCase("all");
        int playerArgIndex = all ? 1 : 0;

        Player target;
        if (args.length > playerArgIndex) {
            if (!sender.hasPermission("hippostaff.repair.others")) {
                sender.sendMessage(Component.translatable("commands.generic.permission"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[playerArgIndex]);
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

        if (all) {
            if (!sender.hasPermission("hippostaff.repair.all")) {
                sender.sendMessage(Component.translatable("commands.generic.permission"));
                return true;
            }
            int repaired = repairAll(target.getInventory());
            if (repaired == 0) {
                sender.sendMessage(Component.text("No repairable items found."));
                return true;
            }
            notifyResult(sender, target, "Repaired " + repaired + " item(s).");
            return true;
        }

        ItemStack hand = target.getInventory().getItemInMainHand();
        if (!repairItem(hand)) {
            sender.sendMessage(Component.text("Hold a damaged repairable item."));
            return true;
        }
        notifyResult(sender, target, "Repaired held item.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if ("all".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                values.add("all");
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    values.add(online.getName());
                }
            }
            return values;
        }
        if (args.length == 2 && "all".equalsIgnoreCase(args[0])) {
            return playerCompletions(args[1]);
        }
        return List.of();
    }

    private List<String> playerCompletions(String input) {
        String current = input.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                completions.add(online.getName());
            }
        }
        return completions;
    }

    private int repairAll(PlayerInventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (repairItem(item)) {
                count++;
            }
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (repairItem(item)) {
                count++;
            }
        }
        if (repairItem(inventory.getItemInOffHand())) {
            count++;
        }
        return count;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType().getMaxDurability() <= 0) {
            return false;
        }
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        if (damageable.getDamage() <= 0) {
            return false;
        }
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return true;
    }

    private void notifyResult(CommandSender sender, Player target, String text) {
        if (sender.equals(target)) {
            sender.sendMessage(Component.text(text));
            return;
        }
        sender.sendMessage(Component.text("Set for " + target.getName() + ": " + text));
        target.sendMessage(Component.text(text));
    }
}
