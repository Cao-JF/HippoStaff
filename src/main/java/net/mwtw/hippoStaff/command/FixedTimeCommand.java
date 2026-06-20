package net.mwtw.hippoStaff.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class FixedTimeCommand implements CommandExecutor {
    private final long time;

    public FixedTimeCommand(long time) {
        this.time = time;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage(Component.translatable("commands.generic.permission"));
            return true;
        }

        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage(Component.translatable("commands.time.query"));
            return true;
        }

        world.setTime(this.time);
        sender.sendMessage(Component.translatable("commands.time.set", Component.text(this.time)));
        return true;
    }

    private boolean canUse(CommandSender sender) {
        return sender.hasPermission("minecraft.command.time")
                || sender.hasPermission("bukkit.command.time")
                || sender.hasPermission("hippostaff.timepreset");
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0);
    }
}
