package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.message.PrivateMessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ReplyCommand implements CommandExecutor {
    private final PrivateMessageManager manager;
    private final MessageService messageService;

    public ReplyCommand(PrivateMessageManager manager, MessageService messageService) {
        this.manager = manager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only", null);
            return true;
        }
        if (!this.manager.canReply(player)) {
            this.messageService.send(player, "errors.no-permission");
            return true;
        }
        if (args.length < 1) {
            this.messageService.send(player, "private-message.reply-usage");
            return true;
        }
        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            this.messageService.send(player, "private-message.reply-usage");
            return true;
        }
        return this.manager.reply(player, message);
    }
}
