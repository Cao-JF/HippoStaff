package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.vanish.PickupSettingsManager;
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

public final class VanishPickupCommand implements CommandExecutor, TabCompleter {
    private final PickupSettingsManager pickupSettingsManager;
    private final MessageService messageService;

    public VanishPickupCommand(PickupSettingsManager pickupSettingsManager, MessageService messageService) {
        this.pickupSettingsManager = pickupSettingsManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only", null);
            return true;
        }
        if (!player.hasPermission("hippostaff.vanish.pickup")) {
            this.messageService.send(player, "errors.no-permission");
            return true;
        }

        boolean enabled;
        if (args.length == 0) {
            enabled = this.pickupSettingsManager.toggle(player.getUniqueId());
        } else {
            String arg = args[0].toLowerCase(Locale.ROOT);
            if ("on".equals(arg) || "true".equals(arg) || "enable".equals(arg)) {
                this.pickupSettingsManager.set(player.getUniqueId(), true);
                enabled = true;
            } else if ("off".equals(arg) || "false".equals(arg) || "disable".equals(arg)) {
                this.pickupSettingsManager.set(player.getUniqueId(), false);
                enabled = false;
            } else {
                this.messageService.send(player, "vanish.pickup.usage");
                return true;
            }
        }

        if (enabled) {
            this.messageService.send(player, "vanish.pickup.enabled");
        } else {
            this.messageService.send(player, "vanish.pickup.disabled");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String current = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if ("on".startsWith(current)) {
            completions.add("on");
        }
        if ("off".startsWith(current)) {
            completions.add("off");
        }
        return completions;
    }
}
