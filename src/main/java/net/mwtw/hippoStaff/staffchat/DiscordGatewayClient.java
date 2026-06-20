package net.mwtw.hippoStaff.staffchat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mwtw.hippoStaff.Core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

public final class DiscordGatewayClient implements WebSocket.Listener {
    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    private final Core plugin;
    private final String token;
    private final String channelId;
    private final BiConsumer<String, String> onMessage; // username, content

    private HttpClient httpClient;
    private WebSocket webSocket;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private StringBuilder buffer = new StringBuilder();
    private int lastSequence = -1;
    private volatile boolean running = false;

    public DiscordGatewayClient(Core plugin, String token, String channelId, BiConsumer<String, String> onMessage) {
        this.plugin = plugin;
        this.token = token;
        this.channelId = channelId;
        this.onMessage = onMessage;
    }

    public void connect() {
        this.running = true;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HippoStaff-Discord");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newHttpClient();
        attemptConnect();
    }

    public void shutdown() {
        this.running = false;
        cancelHeartbeat();
        if (this.scheduler != null) {
            this.scheduler.shutdownNow();
            this.scheduler = null;
        }
        WebSocket ws = this.webSocket;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(e -> null);
            this.webSocket = null;
        }
    }

    @Override
    public void onOpen(WebSocket ws) {
        this.webSocket = ws;
        ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        this.buffer.append(data);
        if (last) {
            String raw = this.buffer.toString();
            this.buffer = new StringBuilder();
            handlePayload(raw);
        }
        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        this.plugin.getLogger().info("Discord gateway closed (" + statusCode + "): " + reason);
        cancelHeartbeat();
        scheduleReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        this.plugin.getLogger().warning("Discord gateway error: " + error.getMessage());
        cancelHeartbeat();
        scheduleReconnect();
    }

    private void handlePayload(String raw) {
        try {
            JsonObject payload = JsonParser.parseString(raw).getAsJsonObject();
            int op = payload.get("op").getAsInt();

            if (payload.has("s") && !payload.get("s").isJsonNull()) {
                this.lastSequence = payload.get("s").getAsInt();
            }

            switch (op) {
                case 10 -> { // HELLO
                    int interval = payload.getAsJsonObject("d").get("heartbeat_interval").getAsInt();
                    startHeartbeat(interval);
                    identify();
                }
                case 0 -> { // DISPATCH
                    String t = payload.has("t") && !payload.get("t").isJsonNull()
                            ? payload.get("t").getAsString() : "";
                    if ("MESSAGE_CREATE".equals(t)) {
                        handleMessageCreate(payload.getAsJsonObject("d"));
                    }
                }
                case 7 -> reconnectNow();  // RECONNECT
                case 9 -> scheduleReconnect(); // INVALID SESSION
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Discord gateway parse error: " + e.getMessage());
        }
    }

    private void handleMessageCreate(JsonObject d) {
        String incomingChannel = d.has("channel_id") ? d.get("channel_id").getAsString() : "";
        if (!this.channelId.equals(incomingChannel)) {
            return;
        }
        JsonObject author = d.has("author") ? d.getAsJsonObject("author") : null;
        if (author == null) {
            return;
        }
        // Ignore bots (including ourselves via webhook)
        if (author.has("bot") && author.get("bot").getAsBoolean()) {
            return;
        }
        String username;
        if (author.has("global_name") && !author.get("global_name").isJsonNull()) {
            username = author.get("global_name").getAsString();
        } else {
            username = author.has("username") ? author.get("username").getAsString() : "Unknown";
        }
        String content = d.has("content") ? d.get("content").getAsString().trim() : "";
        if (content.isEmpty()) {
            return;
        }
        this.onMessage.accept(username, content);
    }

    private void identify() {
        // GUILD_MESSAGES (1<<9 = 512) + MESSAGE_CONTENT (1<<15 = 32768)
        String payload = "{\"op\":2,\"d\":{"
                + "\"token\":\"" + this.token + "\","
                + "\"intents\":33280,"
                + "\"properties\":{\"os\":\"linux\",\"browser\":\"hippostaff\",\"device\":\"hippostaff\"}}}";
        this.webSocket.sendText(payload, true);
    }

    private void startHeartbeat(int intervalMs) {
        cancelHeartbeat();
        this.heartbeatTask = this.scheduler.scheduleAtFixedRate(() -> {
            WebSocket ws = this.webSocket;
            if (ws == null || ws.isOutputClosed()) {
                return;
            }
            String seq = this.lastSequence == -1 ? "null" : String.valueOf(this.lastSequence);
            ws.sendText("{\"op\":1,\"d\":" + seq + "}", true);
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (this.heartbeatTask != null) {
            this.heartbeatTask.cancel(false);
            this.heartbeatTask = null;
        }
    }

    private void reconnectNow() {
        cancelHeartbeat();
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.abort();
            this.webSocket = null;
        }
        if (this.running) {
            attemptConnect();
        }
    }

    private void scheduleReconnect() {
        if (!this.running || this.scheduler == null || this.scheduler.isShutdown()) {
            return;
        }
        this.scheduler.schedule(this::attemptConnect, 5, TimeUnit.SECONDS);
    }

    private void attemptConnect() {
        if (!this.running) {
            return;
        }
        this.lastSequence = -1;
        this.httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(GATEWAY_URL), this)
                .exceptionally(e -> {
                    this.plugin.getLogger().warning("Discord gateway connect failed: " + e.getMessage());
                    scheduleReconnect();
                    return null;
                });
    }
}
