package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.vanish.VanishManager;
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

public final class VanishCommand implements CommandExecutor, TabCompleter {
    private final VanishManager vanishManager;
    private final MessageService messageService;

    public VanishCommand(VanishManager vanishManager, MessageService messageService) {
        this.vanishManager = vanishManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only", null);
            return true;
        }

        if (!player.hasPermission("hippostaff.vanish")) {
            this.messageService.send(player, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            this.vanishManager.toggle(player);
            return true;
        }

        String stateArg = args[0].toLowerCase(Locale.ROOT);
        if (stateArg.equals("on")) {
            this.vanishManager.setVanished(player, true, true);
            return true;
        }
        if (stateArg.equals("off")) {
            this.vanishManager.setVanished(player, false, true);
            return true;
        }

        this.messageService.send(player, "errors.usage");
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
