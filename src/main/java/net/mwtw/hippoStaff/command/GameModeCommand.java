package net.mwtw.hippoStaff.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import java.util.Map;

public final class GameModeCommand implements CommandExecutor, TabCompleter {
    private static final Map<String, GameMode> MODE_TOKENS = Map.ofEntries(
            Map.entry("s", GameMode.SURVIVAL),
            Map.entry("survival", GameMode.SURVIVAL),
            Map.entry("0", GameMode.SURVIVAL),
            Map.entry("c", GameMode.CREATIVE),
            Map.entry("creative", GameMode.CREATIVE),
            Map.entry("1", GameMode.CREATIVE),
            Map.entry("a", GameMode.ADVENTURE),
            Map.entry("adventure", GameMode.ADVENTURE),
            Map.entry("2", GameMode.ADVENTURE),
            Map.entry("sp", GameMode.SPECTATOR),
            Map.entry("spectator", GameMode.SPECTATOR),
            Map.entry("3", GameMode.SPECTATOR)
    );
    private static final List<String> MODE_SUGGESTIONS = List.of("s", "0", "c", "1", "a", "2", "sp", "3");

    private final @Nullable GameMode fixedMode;

    public GameModeCommand(@Nullable GameMode fixedMode) {
        this.fixedMode = fixedMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        GameMode mode = this.fixedMode;
        int targetArgIndex = 0;

        if (mode == null) {
            if (args.length < 1) {
                return false;
            }
            mode = parseMode(args[0]);
            if (mode == null) {
                return false;
            }
            targetArgIndex = 1;
        }

        Player target;
        if (args.length > targetArgIndex) {
            target = Bukkit.getPlayerExact(args[targetArgIndex]);
            if (target == null) {
                sender.sendMessage(Component.translatable("argument.player.notfound"));
                return true;
            }
            if (!canUseOnOthers(sender, mode)) {
                sender.sendMessage(Component.translatable("commands.gamemode.failed"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.translatable("commands.gamemode.failed"));
                return true;
            }
            if (!canUseOnSelf(sender, mode)) {
                sender.sendMessage(Component.translatable("commands.gamemode.failed"));
                return true;
            }
            target = player;
        }

        target.setGameMode(mode);
        Component modeName = modeComponent(mode);

        if (target.equals(sender)) {
            sender.sendMessage(Component.translatable("commands.gamemode.success.self", modeName));
            return true;
        }

        sender.sendMessage(Component.translatable("commands.gamemode.success.other", Component.text(target.getName()), modeName));
        target.sendMessage(Component.translatable("gameMode.changed", modeName));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (this.fixedMode == null) {
            if (args.length == 1) {
                String current = args[0].toLowerCase(Locale.ROOT);
                List<String> completions = new ArrayList<>();
                for (String suggestion : MODE_SUGGESTIONS) {
                    if (suggestion.startsWith(current)) {
                        completions.add(suggestion);
                    }
                }
                return completions;
            }
            if (args.length == 2) {
                return playerCompletions(args[1]);
            }
            return List.of();
        }

        if (args.length == 1) {
            return playerCompletions(args[0]);
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

    private @Nullable GameMode parseMode(String token) {
        return MODE_TOKENS.get(token.toLowerCase(Locale.ROOT));
    }

    private boolean canUseOnSelf(CommandSender sender, GameMode mode) {
        return sender.hasPermission("minecraft.command.gamemode")
                || sender.hasPermission("bukkit.command.gamemode")
                || sender.hasPermission("minecraft.command.gamemode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private boolean canUseOnOthers(CommandSender sender, GameMode mode) {
        if (!canUseOnSelf(sender, mode)) {
            return false;
        }
        return sender.hasPermission("minecraft.command.gamemode")
                || sender.hasPermission("bukkit.command.gamemode")
                || sender.hasPermission("minecraft.command.gamemode.other");
    }

    private Component modeComponent(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> Component.translatable("gameMode.survival");
            case CREATIVE -> Component.translatable("gameMode.creative");
            case ADVENTURE -> Component.translatable("gameMode.adventure");
            case SPECTATOR -> Component.translatable("gameMode.spectator");
        };
    }
}
