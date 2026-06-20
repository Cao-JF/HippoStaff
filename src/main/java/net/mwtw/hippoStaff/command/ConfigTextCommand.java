package net.mwtw.hippoStaff.command;

import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ConfigTextCommand implements CommandExecutor {
    private final Core plugin;
    private final MessageService messageService;
    private final String path;

    public ConfigTextCommand(Core plugin, MessageService messageService, String path) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.path = path;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            return true;
        }

        Object value = this.plugin.getConfig().get(this.path);
        if (value instanceof String text) {
            sendLine(sender, text);
            return true;
        }

        if (value instanceof List<?> lines) {
            for (Object line : lines) {
                sendLine(sender, line == null ? "" : String.valueOf(line));
            }
        }
        return true;
    }

    private void sendLine(CommandSender sender, String line) {
        sender.sendMessage(this.messageService.parse(line));
    }
}
