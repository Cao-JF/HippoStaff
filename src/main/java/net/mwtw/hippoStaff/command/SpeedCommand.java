package net.mwtw.hippoStaff.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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

public final class SpeedCommand implements CommandExecutor, TabCompleter {
    private final @Nullable SpeedType fixedType;

    public SpeedCommand(@Nullable SpeedType fixedType) {
        this.fixedType = fixedType;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        SpeedType type = this.fixedType;
        int valueIndex = 0;
        int playerIndex = 1;

        if (type == null) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /speed <fly|walk> <0-10> [player]"));
                return true;
            }
            type = SpeedType.fromInput(args[0]);
            if (type == null) {
                sender.sendMessage(Component.text("Invalid mode. Use fly or walk."));
                return true;
            }
            valueIndex = 1;
            playerIndex = 2;
        } else if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /" + label + " <0-10> [player]"));
            return true;
        }

        if (!sender.hasPermission("hippostaff.speed." + type.permissionNode())) {
            sender.sendMessage(Component.translatable("commands.generic.permission"));
            return true;
        }

        Player target;
        if (args.length > playerIndex) {
            if (!sender.hasPermission("hippostaff.speed.others")) {
                sender.sendMessage(Component.translatable("commands.generic.permission"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[playerIndex]);
            if (target == null) {
                sender.sendMessage(Component.translatable("argument.player.notfound"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Console must specify a player."));
                return true;
            }
            target = player;
        }

        Float parsed = parseSpeedValue(args[valueIndex]);
        boolean reset = parsed == null && "reset".equalsIgnoreCase(args[valueIndex]);
        if (!reset && parsed == null) {
            sender.sendMessage(Component.text("Speed must be a number from 0 to 10, or 'reset'."));
            return true;
        }

        if (type == SpeedType.FLY) {
            target.setFlySpeed(reset ? 0.1f : parsed);
        } else {
            target.setWalkSpeed(reset ? 0.2f : parsed);
        }

        int displayValue = reset ? (type == SpeedType.FLY ? 1 : 2) : Math.round(parsed * 10.0f);
        String modeName = type.name().toLowerCase(Locale.ROOT);
        if (target.equals(sender)) {
            sender.sendMessage(Component.text((reset ? "Reset " : "Set ") + modeName + " speed to " + displayValue + "."));
        } else {
            sender.sendMessage(Component.text((reset ? "Reset " : "Set ") + target.getName() + "'s " + modeName + " speed to " + displayValue + "."));
            target.sendMessage(Component.text("Your " + modeName + " speed was " + (reset ? "reset" : "set") + " to " + displayValue + "."));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (this.fixedType == null) {
            if (args.length == 1) {
                return partial(args[0], List.of("fly", "walk"));
            }
            if (args.length == 2) {
                return partial(args[1], List.of("reset", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
            }
            if (args.length == 3) {
                return playerCompletions(args[2]);
            }
            return List.of();
        }

        if (args.length == 1) {
            return partial(args[0], List.of("reset", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"));
        }
        if (args.length == 2) {
            return playerCompletions(args[1]);
        }
        return List.of();
    }

    private @Nullable Float parseSpeedValue(String input) {
        try {
            float value = Float.parseFloat(input);
            if (value < 0.0f || value > 10.0f) {
                return null;
            }
            return value / 10.0f;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> playerCompletions(String input) {
        String current = input.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                results.add(online.getName());
            }
        }
        return results;
    }

    private List<String> partial(String input, List<String> values) {
        String current = input.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(current)) {
                results.add(value);
            }
        }
        return results;
    }

    public enum SpeedType {
        FLY("fly"),
        WALK("walk");

        private final String permissionNode;

        SpeedType(String permissionNode) {
            this.permissionNode = permissionNode;
        }

        public String permissionNode() {
            return this.permissionNode;
        }

        public static @Nullable SpeedType fromInput(String input) {
            return switch (input.toLowerCase(Locale.ROOT)) {
                case "fly", "f" -> FLY;
                case "walk", "w" -> WALK;
                default -> null;
            };
        }
    }
}
