package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.staffchat.StaffChatManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class StaffChatCommand implements CommandExecutor {
    private final StaffChatManager staffChatManager;
    private final MessageService messageService;

    public StaffChatCommand(StaffChatManager staffChatManager, MessageService messageService) {
        this.staffChatManager = staffChatManager;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            this.messageService.send(sender, "errors.player-only", null);
            return true;
        }
        if (!this.staffChatManager.canUse(player)) {
            this.messageService.send(player, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            boolean enabled = this.staffChatManager.toggle(player.getUniqueId());
            this.messageService.send(player, enabled ? "staffchat.toggle-on" : "staffchat.toggle-off");
            return true;
        }

        String message = String.join(" ", args).trim();
        if (!message.isEmpty()) {
            this.staffChatManager.sendFromPlayer(player, message, false);
        }
        return true;
    }
}
