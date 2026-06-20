package net.mwtw.hippoStaff.staffchat;

import net.mwtw.hippoStaff.Core;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class DiscordService {
    private final Core plugin;
    private DiscordGatewayClient gatewayClient;

    public DiscordService(Core plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the Discord bot gateway for receiving Discord → Minecraft messages.
     * onDiscordMessage is called on the Bukkit main thread with (username, content).
     */
    public void start(BiConsumer<String, String> onDiscordMessage) {
        stop();
        if (!isEnabled()) {
            return;
        }
        String token = this.plugin.getConfig().getString("staff-chat.discord.bot-token", "").trim();
        String channelId = this.plugin.getConfig().getString("staff-chat.discord.channel-id", "").trim();
        if (token.isEmpty() || channelId.isEmpty()) {
            return;
        }
        // Wrap callback onto the main thread
        BiConsumer<String, String> mainThreadCallback = (username, content) ->
                this.plugin.getServer().getScheduler().runTask(this.plugin, () -> onDiscordMessage.accept(username, content));
        this.gatewayClient = new DiscordGatewayClient(this.plugin, token, channelId, mainThreadCallback);
        this.gatewayClient.connect();
        this.plugin.getLogger().info("Discord gateway connected.");
    }

    public void stop() {
        if (this.gatewayClient != null) {
            this.gatewayClient.shutdown();
            this.gatewayClient = null;
        }
    }

    /**
     * Send a Minecraft staff chat message to Discord via webhook.
     * Uses username + avatar_url override so it appears as the player, not the bot.
     */
    public void sendToDiscord(String playerName, UUID playerUuid, String rank, String server, String message) {
        if (!isEnabled()) {
            return;
        }
        String webhookUrl = this.plugin.getConfig().getString("staff-chat.discord.webhook-url", "").trim();
        if (webhookUrl.isEmpty()) {
            return;
        }

        String headTemplate = this.plugin.getConfig().getString(
                "staff-chat.discord.head-url",
                "https://mc-heads.net/avatar/%%player%%/64"
        );
        String headUrl = headTemplate
                .replace("%%player%%", playerName)
                .replace("%%uuid%%", playerUuid.toString());

        String displayRank = rank.isEmpty() ? "" : Character.toUpperCase(rank.charAt(0)) + rank.substring(1);
        String username = displayRank.isEmpty() ? playerName : displayRank + " | " + playerName;

        String payload = "{\"username\":\"" + escape(username) + "\","
                + "\"avatar_url\":\"" + escape(headUrl) + "\","
                + "\"content\":\"" + escape("[" + server + "] " + message) + "\","
                + "\"allowed_mentions\":{\"parse\":[]}}";

        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(webhookUrl).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                connection.disconnect();
                if (code >= 400) {
                    this.plugin.getLogger().warning("Discord webhook returned HTTP " + code + ".");
                }
            } catch (Exception e) {
                this.plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
            }
        });
    }

    private boolean isEnabled() {
        return this.plugin.getConfig().getBoolean("staff-chat.discord.enabled", false);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
