package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.message.PrivateMessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MessageCommand implements CommandExecutor, TabCompleter {
    private final PrivateMessageManager manager;
    private final MessageService messageService;

    public MessageCommand(PrivateMessageManager manager, MessageService messageService) {
        this.manager = manager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only", null);
            return true;
        }
        if (!this.manager.canUse(player)) {
            this.messageService.send(player, "errors.no-permission");
            return true;
        }
        if (args.length < 2) {
            this.messageService.send(player, "private-message.usage");
            return true;
        }
        String target = args[0];
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty()) {
            this.messageService.send(player, "private-message.usage");
            return true;
        }
        return this.manager.sendMessage(player, target, message);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return this.manager.tabCompleteOnlinePlayers(args[0]);
        }
        return List.of();
    }
}
