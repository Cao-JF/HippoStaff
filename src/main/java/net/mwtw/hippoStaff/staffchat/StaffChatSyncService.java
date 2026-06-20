package net.mwtw.hippoStaff.staffchat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import net.mwtw.hippoStaff.Core;

import java.time.Duration;
import java.util.UUID;

public final class StaffChatSyncService {
    private final Core plugin;
    private final StaffChatManager manager;
    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> subscriberConnection;
    private StatefulRedisPubSubConnection<String, String> publisherConnection;
    private RedisPubSubCommands<String, String> publisher;
    private String channel;
    private String localServerId;

    public StaffChatSyncService(Core plugin, StaffChatManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void init() {
        this.channel = this.plugin.getConfig().getString("staff-chat.sync.channel", "hippostaff:staffchat");
        this.localServerId = this.plugin.getConfig().getString("server-id", "unknown");

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(this.plugin.getConfig().getString("storage.redis.host", "127.0.0.1"))
                .withPort(this.plugin.getConfig().getInt("storage.redis.port", 6379))
                .withDatabase(this.plugin.getConfig().getInt("storage.redis.database", 0))
                .withTimeout(Duration.ofSeconds(5));

        String password = this.plugin.getConfig().getString("storage.redis.password", "");
        if (password != null && !password.isBlank()) {
            builder.withPassword(password.toCharArray());
        }

        this.redisClient = RedisClient.create(builder.build());
        this.publisherConnection = this.redisClient.connectPubSub();
        this.subscriberConnection = this.redisClient.connectPubSub();
        this.publisher = this.publisherConnection.sync();

        this.subscriberConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String incomingChannel, String message) {
                if (!channel.equals(incomingChannel)) {
                    return;
                }
                StaffPacket packet = StaffPacket.parse(message);
                if (packet == null || localServerId.equalsIgnoreCase(packet.serverId())) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> manager.dispatchRemote(packet.senderUuid(), packet.senderName(), packet.serverId(), packet.formattedChat(), packet.formattedConsole()));
            }
        });

        this.subscriberConnection.async().subscribe(this.channel);
    }

    public void publish(UUID senderUuid, String senderName, String serverId, String formattedChat, String formattedConsole) {
        if (this.publisher == null) {
            return;
        }
        this.publisher.publish(this.channel, StaffPacket.encode(senderUuid, senderName, serverId, formattedChat, formattedConsole));
    }

    public void close() {
        if (this.publisherConnection != null) {
            this.publisherConnection.close();
        }
        if (this.subscriberConnection != null) {
            this.subscriberConnection.close();
        }
        if (this.redisClient != null) {
            this.redisClient.shutdown();
        }
    }

    // packet format: uuid|name|server|formattedChat|formattedConsole
    private record StaffPacket(UUID senderUuid, String senderName, String serverId, String formattedChat, String formattedConsole) {
        static String encode(UUID senderUuid, String senderName, String serverId, String formattedChat, String formattedConsole) {
            return senderUuid + "|" + sanitize(senderName) + "|" + sanitize(serverId) + "|" + sanitize(formattedChat) + "|" + sanitize(formattedConsole);
        }

        static StaffPacket parse(String input) {
            String[] parts = input.split("\\|", 5);
            if (parts.length != 5) {
                return null;
            }
            try {
                return new StaffPacket(UUID.fromString(parts[0]), unsanitize(parts[1]), unsanitize(parts[2]), unsanitize(parts[3]), unsanitize(parts[4]));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private static String sanitize(String input) {
            return input.replace("\\", "\\\\").replace("|", "\\p");
        }

        private static String unsanitize(String input) {
            StringBuilder out = new StringBuilder();
            boolean escaped = false;
            for (int i = 0; i < input.length(); i++) {
                char ch = input.charAt(i);
                if (escaped) {
                    if (ch == 'p') {
                        out.append('|');
                    } else {
                        out.append(ch);
                    }
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                out.append(ch);
            }
            if (escaped) {
                out.append('\\');
            }
            return out.toString();
        }
    }
}
