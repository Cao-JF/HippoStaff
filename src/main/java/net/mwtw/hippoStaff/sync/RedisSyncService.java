package net.mwtw.hippoStaff.sync;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import net.mwtw.hippoStaff.Core;

import java.time.Duration;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class RedisSyncService {
    private final Core plugin;
    private final BiConsumer<UUID, Boolean> updateConsumer;
    private final UUID serverId = UUID.randomUUID();
    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> subscriberConnection;
    private StatefulRedisPubSubConnection<String, String> publisherConnection;
    private RedisPubSubAsyncCommands<String, String> publisher;
    private String channel;

    public RedisSyncService(Core plugin, BiConsumer<UUID, Boolean> updateConsumer) {
        this.plugin = plugin;
        this.updateConsumer = updateConsumer;
    }

    public void init() {
        this.channel = this.plugin.getConfig().getString("storage.redis.channel", "hippostaff:vanish");

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
        this.publisher = this.publisherConnection.async();

        this.subscriberConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String incomingChannel, String message) {
                if (!channel.equals(incomingChannel)) {
                    return;
                }
                SyncPacket packet = SyncPacket.parse(message);
                if (packet == null) {
                    return;
                }
                // Ignore messages this server published itself.
                if (serverId.equals(packet.origin())) {
                    return;
                }
                updateConsumer.accept(packet.uuid(), packet.vanished());
            }
        });

        this.subscriberConnection.async().subscribe(this.channel);
    }

    public void publish(UUID uuid, boolean vanished) {
        if (this.publisher == null) {
            return;
        }
        this.publisher.publish(this.channel, SyncPacket.encode(this.serverId, uuid, vanished));
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

    private record SyncPacket(UUID origin, UUID uuid, boolean vanished) {
        static String encode(UUID origin, UUID uuid, boolean vanished) {
            return origin + ":" + uuid + ":" + vanished;
        }

        static SyncPacket parse(String input) {
            String[] parts = input.split(":", 3);
            if (parts.length != 3) {
                return null;
            }
            try {
                return new SyncPacket(UUID.fromString(parts[0]), UUID.fromString(parts[1]), Boolean.parseBoolean(parts[2]));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
