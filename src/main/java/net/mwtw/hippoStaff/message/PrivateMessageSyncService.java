package net.mwtw.hippoStaff.message;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.api.sync.RedisCommands;
import net.mwtw.hippoStaff.Core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PrivateMessageSyncService {
    private final Core plugin;
    private final NetworkPlayerRegistry registry;
    private final PrivateMessageManager messageManager;
    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> subscriberConnection;
    private StatefulRedisPubSubConnection<String, String> publisherConnection;
    private RedisPubSubCommands<String, String> publisher;
    private RedisCommands<String, String> kv;
    private String localServerId;
    private String channel;
    private String knownPlayersKey;
    private String onlinePlayersKey;

    public PrivateMessageSyncService(Core plugin, NetworkPlayerRegistry registry, PrivateMessageManager messageManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.messageManager = messageManager;
    }

    public void init() {
        this.localServerId = this.plugin.getConfig().getString("server-id", "unknown");
        this.channel = this.plugin.getConfig().getString("private-message.sync.channel", "hippostaff:pm");
        this.knownPlayersKey = this.plugin.getConfig().getString("private-message.sync.known-players-key", "hippostaff:players");
        this.onlinePlayersKey = this.plugin.getConfig().getString("private-message.sync.online-players-key", "hippostaff:players:online");

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
        this.kv = this.redisClient.connect().sync();

        bootstrapRegistryFromRedis();

        this.subscriberConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String incomingChannel, String message) {
                if (!channel.equals(incomingChannel)) {
                    return;
                }
                Packet packet = Packet.parse(message);
                if (packet == null) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> handlePacket(packet));
            }
        });

        this.subscriberConnection.async().subscribe(this.channel);
    }

    public void publishPresence(UUID uuid, String name, boolean join) {
        if (this.publisher == null || this.kv == null) {
            return;
        }
        this.kv.hset(this.knownPlayersKey, uuid.toString(), name);
        if (join) {
            this.kv.hset(this.onlinePlayersKey, uuid.toString(), this.localServerId + "|" + name);
        } else {
            this.kv.hdel(this.onlinePlayersKey, uuid.toString());
        }
        this.publisher.publish(this.channel, Packet.presence(uuid, name, this.localServerId, join).encode());
    }

    public void publishMessage(UUID fromUuid, String fromName, UUID toUuid, String message) {
        if (this.publisher == null) {
            return;
        }
        this.publisher.publish(this.channel, Packet.message(fromUuid, fromName, toUuid, message).encode());
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

    private void bootstrapRegistryFromRedis() {
        if (this.kv == null) {
            return;
        }
        Map<String, String> known = this.kv.hgetall(this.knownPlayersKey);
        List<NetworkPlayerRegistry.PlayerRecord> records = new ArrayList<>();
        for (Map.Entry<String, String> entry : known.entrySet()) {
            try {
                records.add(new NetworkPlayerRegistry.PlayerRecord(UUID.fromString(entry.getKey()), entry.getValue()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.registry.replaceKnownPlayers(records);

        Map<String, String> online = this.kv.hgetall(this.onlinePlayersKey);
        Map<UUID, String> onlineByUuid = new HashMap<>();
        for (Map.Entry<String, String> entry : online.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                String[] parts = entry.getValue().split("\\|", 2);
                String name = parts.length == 2 ? parts[1] : entry.getValue();
                onlineByUuid.put(uuid, name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        this.registry.replaceOnline(onlineByUuid);
    }

    private void handlePacket(Packet packet) {
        if (packet.type() == PacketType.MESSAGE) {
            this.messageManager.handleIncoming(packet.fromUuid(), packet.fromName(), packet.toUuid(), packet.payload());
            return;
        }
        if (packet.type() == PacketType.PRESENCE) {
            if (packet.fromUuid() == null || packet.fromName() == null) {
                return;
            }
            this.registry.setOnline(packet.fromUuid(), packet.fromName(), "join".equals(packet.payload()));
        }
    }

    private enum PacketType {
        MESSAGE,
        PRESENCE
    }

    private record Packet(PacketType type, UUID fromUuid, String fromName, UUID toUuid, String serverId, String payload) {
        static Packet message(UUID fromUuid, String fromName, UUID toUuid, String message) {
            return new Packet(PacketType.MESSAGE, fromUuid, fromName, toUuid, null, message);
        }

        static Packet presence(UUID uuid, String name, String serverId, boolean join) {
            return new Packet(PacketType.PRESENCE, uuid, name, null, serverId, join ? "join" : "quit");
        }

        String encode() {
            String typeValue = this.type == PacketType.MESSAGE ? "MSG" : "PRES";
            return String.join("|",
                    typeValue,
                    encodeField(this.fromUuid == null ? "" : this.fromUuid.toString()),
                    encodeField(this.fromName == null ? "" : this.fromName),
                    encodeField(this.toUuid == null ? "" : this.toUuid.toString()),
                    encodeField(this.serverId == null ? "" : this.serverId),
                    encodeField(this.payload == null ? "" : this.payload)
            );
        }

        static Packet parse(String input) {
            try {
                String[] parts = input.split("\\|", 6);
                if (parts.length != 6) {
                    return null;
                }

                String typeRaw = parts[0];
                String fromUuidRaw = decodeField(parts[1]);
                String fromName = decodeField(parts[2]);
                String toUuidRaw = decodeField(parts[3]);
                String serverId = decodeField(parts[4]);
                String payload = decodeField(parts[5]);

                if ("MSG".equals(typeRaw)) {
                    return new Packet(
                            PacketType.MESSAGE,
                            UUID.fromString(fromUuidRaw),
                            fromName,
                            UUID.fromString(toUuidRaw),
                            serverId.isBlank() ? null : serverId,
                            payload
                    );
                }
                if ("PRES".equals(typeRaw)) {
                    return new Packet(
                            PacketType.PRESENCE,
                            UUID.fromString(fromUuidRaw),
                            fromName,
                            null,
                            serverId,
                            payload
                    );
                }
            } catch (IllegalArgumentException ignored) {
                return null;
            }
            return null;
        }

        private static String encodeField(String input) {
            return Base64.getEncoder().encodeToString(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        private static String decodeField(String input) {
            byte[] bytes = Base64.getDecoder().decode(input);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
