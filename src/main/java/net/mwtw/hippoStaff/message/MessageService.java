package net.mwtw.hippoStaff.message;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mwtw.hippoStaff.Core;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final Core plugin;
    private final MiniMessage miniMessage;
    private YamlConfiguration messages;

    public MessageService(Core plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        File messagesFile = new File(this.plugin.getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void send(CommandSender sender, String key, OfflinePlayer placeholderPlayer) {
        String rawMessage = compose(key, placeholderPlayer, Map.of());
        sender.sendMessage(deserialize(rawMessage));
    }

    public void send(CommandSender sender, String key) {
        Player player = sender instanceof Player playerSender ? playerSender : null;
        send(sender, key, player);
    }

    public void send(CommandSender sender, String key, OfflinePlayer placeholderPlayer, Map<String, String> replacements) {
        String rawMessage = compose(key, placeholderPlayer, replacements);
        sender.sendMessage(deserialize(rawMessage));
    }

    public void actionbar(Player player, String key) {
        String rawMessage = applyPlaceholders(player, readOrDefault(key, "<red>Missing message: " + key));
        player.sendActionBar(deserialize(rawMessage));
    }

    public String raw(String key, OfflinePlayer player) {
        return applyPlaceholders(player, readOrDefault(key, ""));
    }

    public String raw(String key, OfflinePlayer player, Map<String, String> replacements) {
        String raw = applyPlaceholders(player, readOrDefault(key, ""));
        return applyReplacements(raw, replacements);
    }

    public List<String> rawList(String key, OfflinePlayer player, Map<String, String> replacements) {
        List<String> lines = this.messages.getStringList(key);
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(applyReplacements(applyPlaceholders(player, line), replacements));
        }
        return out;
    }

    public Component parse(String input) {
        return deserialize(input);
    }

    private String compose(String key, OfflinePlayer player, Map<String, String> replacements) {
        String prefix = useGlobalPrefix(key) ? readOrDefault("format.prefix", "") : "";
        String body = readOrDefault(key, "<red>Missing message: " + key);
        String withReplacements = applyReplacements(prefix + body, replacements);
        return applyPlaceholders(player, withReplacements);
    }

    private boolean useGlobalPrefix(String key) {
        return !key.startsWith("staffchat.") && !key.startsWith("private-message.");
    }

    private String readOrDefault(String key, String fallback) {
        return this.messages.getString(key, fallback);
    }

    private String applyPlaceholders(OfflinePlayer player, String input) {
        if (player == null || !this.plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return input;
        }
        return PlaceholderAPI.setPlaceholders(player, input);
    }

    private String applyReplacements(String input, Map<String, String> replacements) {
        String output = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace("%%" + entry.getKey() + "%%", entry.getValue());
        }
        return output;
    }

    private Component deserialize(String input) {
        return this.miniMessage.deserialize(legacyToMini(input));
    }

    private String legacyToMini(String input) {
        StringBuilder out = new StringBuilder(input.length() + 32);
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String tag = legacyCodeToMiniTag(code);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
                if (code == 'x' && i + 13 < input.length()) {
                    String hex = parseLegacyHex(input, i + 2);
                    if (hex != null) {
                        out.append("<#").append(hex).append(">");
                        i += 13;
                        continue;
                    }
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String legacyCodeToMiniTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    private String parseLegacyHex(String input, int startIndex) {
        StringBuilder hex = new StringBuilder(6);
        int index = startIndex;
        for (int n = 0; n < 6; n++) {
            if (index + 1 >= input.length() || input.charAt(index) != '&') {
                return null;
            }
            char nibble = input.charAt(index + 1);
            if (!isHex(nibble)) {
                return null;
            }
            hex.append(nibble);
            index += 2;
        }
        return hex.toString();
    }

    private boolean isHex(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f');
    }
}
