package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.vanish.VanishManager;
import net.mwtw.hippoStaff.vanish.rules.RuleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public final class PlayerConnectionListener implements Listener {
    private final Core plugin;
    private final VanishManager vanishManager;
    private final RuleManager ruleManager;

    public PlayerConnectionListener(Core plugin, VanishManager vanishManager, RuleManager ruleManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
        this.ruleManager = ruleManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.vanishManager.handleJoin(player);
        runJoinCommands(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.ruleManager.clearChatConfirm(event.getPlayer().getUniqueId());
        this.vanishManager.handleQuit(event.getPlayer());
    }

    private void runJoinCommands(Player player) {
        if (!this.plugin.getConfig().getBoolean("onjoin.enabled", false)) {
            return;
        }
        List<String> commands = this.plugin.getConfig().getStringList("onjoin.commands");
        if (commands.isEmpty()) {
            return;
        }
        long delayTicks = Math.max(1L, this.plugin.getConfig().getLong("onjoin.delay", 20L));

        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (String rawCommand : commands) {
                String command = rawCommand.trim();
                if (command.isEmpty()) {
                    continue;
                }
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                command = command
                        .replace("%player%", player.getName())
                        .replace("{player}", player.getName());
                player.performCommand(command);
            }
        }, delayTicks);
    }
}
