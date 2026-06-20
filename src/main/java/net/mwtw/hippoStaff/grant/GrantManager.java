package net.mwtw.hippoStaff.grant;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.LuckPermsProvider;
import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GrantManager {
    private final Core plugin;
    private final MessageService messageService;
    private final GrantStorage storage;
    private final Map<UUID, List<GrantRecord>> activeGrants = new ConcurrentHashMap<>();
    private final Map<UUID, List<GrantRecord>> history = new ConcurrentHashMap<>();
    private int expirationTaskId = -1;

    public GrantManager(Core plugin, MessageService messageService, GrantStorage storage) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.storage = storage;
    }

    public void init() throws Exception {
        this.storage.init();
        this.activeGrants.putAll(this.storage.loadActiveGrants());
        this.history.putAll(this.storage.loadHistory());
        long intervalTicks = Math.max(20L, this.plugin.getConfig().getLong("grant-system.expiration-check-interval-ticks", 200L));
        this.expirationTaskId = this.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this.plugin, this::expireGrants, intervalTicks, intervalTicks);
    }

    public void close() {
        if (this.expirationTaskId != -1) {
            this.plugin.getServer().getScheduler().cancelTask(this.expirationTaskId);
            this.expirationTaskId = -1;
        }
        flush();
        this.storage.close();
    }

    public CompletableFuture<UUID> resolveUniqueId(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(playerName);
        if (cached != null && cached.getUniqueId() != null) {
            return CompletableFuture.completedFuture(cached.getUniqueId());
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            return luckPerms.getUserManager().lookupUniqueId(playerName)
                    .thenApply(uuid -> {
                        if (uuid == null) {
                            throw new IllegalArgumentException("Player not found");
                        }
                        return uuid;
                    });
        } catch (IllegalStateException exception) {
            CompletableFuture<UUID> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("LuckPerms is not loaded"));
            return future;
        }
    }

    public List<String> getGrantableGroups() {
        return getGrantableGroupEntries().stream().map(GrantableGroup::name).toList();
    }

    public List<GrantableGroup> getGrantableGroupEntries() {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getGroupManager().loadAllGroups().join();

        List<GrantableGroup> groups = new ArrayList<>();
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            QueryOptions queryOptions = luckPerms.getContextManager().getStaticQueryOptions();
            boolean explicitlyGrantable = group.getNodes(NodeType.PERMISSION).stream()
                    .map(PermissionNode.class::cast)
                    .anyMatch(node -> node.getPermission().equalsIgnoreCase("hippostaff.grantable") && node.getValue());
            if (explicitlyGrantable) {
                CachedMetaData metaData = group.getCachedData().getMetaData(queryOptions);
                String prefix = metaData.getPrefix() == null ? "" : metaData.getPrefix();
                int weight = group.getWeight().orElse(0);
                groups.add(new GrantableGroup(group.getName().toLowerCase(Locale.ROOT), prefix, weight));
            }
        }
        groups.sort(Comparator
                .comparingInt(GrantableGroup::weight).reversed()
                .thenComparing(GrantableGroup::name));
        return groups;
    }

    public CompletableFuture<Void> grant(String targetName, UUID targetUuid, String groupName, String actorName, Duration duration, String reason) {
        return modifyUser(targetUuid, targetName, user -> {
            long now = Instant.now().toEpochMilli();
            Long expiresAt = duration == null ? null : now + duration.toMillis();
            InheritanceNode.Builder builder = InheritanceNode.builder(groupName);
            if (expiresAt != null) {
                builder.expiry(Instant.ofEpochMilli(expiresAt));
            }
            InheritanceNode node = builder.build();
            user.data().add(node);

            GrantRecord record = new GrantRecord(targetUuid, targetName, groupName, actorName, reason, now, expiresAt, GrantAction.GRANTED);
            this.activeGrants.computeIfAbsent(targetUuid, ignored -> new ArrayList<>()).add(record);
            appendHistory(record);
            flush();
            sendWebhook("created", record);
        }).thenAccept(v -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player != null) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("group", groupName);
                replacements.put("actor", actorName);
                replacements.put("duration", duration == null ? "permanent" : formatDuration(duration));
                this.messageService.send(player, "grant.notify.granted", player, replacements);
            }
        }));
    }

    public CompletableFuture<Void> revoke(String targetName, UUID targetUuid, String groupName, String actorName) {
        return modifyUser(targetUuid, targetName, user -> {
            user.data().clear(NodeType.INHERITANCE.predicate(node ->
                    node.getGroupName().equalsIgnoreCase(groupName)));

            long now = Instant.now().toEpochMilli();
            GrantRecord record = new GrantRecord(targetUuid, targetName, groupName, actorName, "manual revoke", now, null, GrantAction.REVOKED);
            List<GrantRecord> records = this.activeGrants.getOrDefault(targetUuid, new ArrayList<>());
            records.removeIf(existing -> existing.group().equalsIgnoreCase(groupName));
            if (records.isEmpty()) {
                this.activeGrants.remove(targetUuid);
            } else {
                this.activeGrants.put(targetUuid, records);
            }
            appendHistory(record);
            flush();
            sendWebhook("revoked", record);
        }).thenAccept(v -> Bukkit.getScheduler().runTask(this.plugin, () -> {
            Player player = Bukkit.getPlayer(targetUuid);
            if (player != null) {
                Map<String, String> replacements = new HashMap<>();
                replacements.put("group", groupName);
                replacements.put("actor", actorName);
                this.messageService.send(player, "grant.notify.revoked", player, replacements);
            }
        }));
    }

    public List<GrantRecord> active(UUID targetUuid) {
        return this.activeGrants.getOrDefault(targetUuid, List.of())
                .stream()
                .sorted(Comparator.comparingLong(GrantRecord::grantedAtEpochMillis).reversed())
                .toList();
    }

    public List<GrantRecord> history(UUID targetUuid) {
        return this.history.getOrDefault(targetUuid, List.of())
                .stream()
                .sorted(Comparator.comparingLong(GrantRecord::grantedAtEpochMillis).reversed())
                .toList();
    }

    public String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds <= 0) {
            return "0s";
        }
        long weeks = seconds / 604800;
        seconds %= 604800;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder out = new StringBuilder();
        if (weeks > 0) out.append(weeks).append("w");
        if (days > 0) out.append(days).append("d");
        if (hours > 0) out.append(hours).append("h");
        if (minutes > 0) out.append(minutes).append("m");
        if (seconds > 0) out.append(seconds).append("s");
        return out.toString();
    }

    private CompletableFuture<Void> modifyUser(UUID targetUuid, String targetName, java.util.function.Consumer<User> edit) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        return luckPerms.getUserManager().loadUser(targetUuid, targetName)
                .thenCompose(user -> {
                    edit.accept(user);
                    return luckPerms.getUserManager().saveUser(user);
                });
    }

    private void appendHistory(GrantRecord record) {
        this.history.computeIfAbsent(record.targetUuid(), ignored -> new ArrayList<>()).add(record);
        this.storage.appendHistory(record);
    }

    private void expireGrants() {
        long now = Instant.now().toEpochMilli();
        List<GrantRecord> toExpire = new ArrayList<>();
        for (List<GrantRecord> records : this.activeGrants.values()) {
            for (GrantRecord record : records) {
                if (record.expiresAtEpochMillis() != null && record.expiresAtEpochMillis() <= now) {
                    toExpire.add(record);
                }
            }
        }

        for (GrantRecord record : toExpire) {
            revoke(record.targetName(), record.targetUuid(), record.group(), "System")
                    .thenRun(() -> {
                        GrantRecord expiredEvent = new GrantRecord(
                                record.targetUuid(),
                                record.targetName(),
                                record.group(),
                                "System",
                                "expired",
                                now,
                                record.expiresAtEpochMillis(),
                                GrantAction.EXPIRED
                        );
                        appendHistory(expiredEvent);
                        flush();
                        sendWebhook("expired", expiredEvent);
                    })
                    .exceptionally(exception -> {
                        this.plugin.getLogger().warning("Failed to expire grant for " + record.targetName() + ": " + exception.getMessage());
                        return null;
                    });
        }
    }

    private void flush() {
        this.storage.save(new HashMap<>(this.activeGrants), new HashMap<>(this.history));
    }

    private void sendWebhook(String eventType, GrantRecord record) {
        if (!this.plugin.getConfig().getBoolean("grant-system.webhook.enabled", false)) {
            return;
        }
        String url = this.plugin.getConfig().getString("grant-system.webhook.url", "").trim();
        if (url.isEmpty()) {
            return;
        }
        String content = "[Grant " + eventType.toUpperCase(Locale.ROOT) + "] target=" + record.targetName() + " group=" + record.group() + " by=" + record.grantedBy();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                String payload = "{\"content\":\"" + escapeJson(content) + "\"}";
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
                connection.disconnect();
            } catch (Exception exception) {
                this.plugin.getLogger().warning("Grant webhook failed: " + exception.getMessage());
            }
        });
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
