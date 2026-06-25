package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VanishListCommand implements CommandExecutor {
    private final VanishManager vanishManager;
    private final MessageService messageService;

    public VanishListCommand(VanishManager vanishManager, MessageService messageService) {
        this.vanishManager = vanishManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("hippostaff.vanish.list")) {
            this.messageService.send(sender, "errors.no-permission");
            return true;
        }

        if (!this.vanishManager.isFeatureEnabled()) {
            this.messageService.send(sender, "vanish.feature-disabled");
            return true;
        }

        Set<UUID> vanished = this.vanishManager.getVanishedPlayersSnapshot();
        List<Player> onlineVanished = new ArrayList<>();
        for (UUID uuid : vanished) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                onlineVanished.add(online);
            }
        }
        if (onlineVanished.isEmpty()) {
            this.messageService.send(sender, "vanish.list.empty");
            return true;
        }

        this.messageService.send(sender, "vanish.list.header", resolvePlayer(sender), Map.of("count", String.valueOf(onlineVanished.size())));
        for (Player online : onlineVanished) {
            this.messageService.send(sender, "vanish.list.line", resolvePlayer(sender), Map.of("player", online.getName()));
        }
        this.messageService.send(sender, "vanish.list.footer");
        return true;
    }

    private Player resolvePlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
