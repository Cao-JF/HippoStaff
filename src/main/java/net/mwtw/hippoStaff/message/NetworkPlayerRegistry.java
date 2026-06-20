package net.mwtw.hippoStaff.message;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkPlayerRegistry {
    private final Map<UUID, String> namesByUuid;
    private final Map<String, UUID> uuidsByLowerName;
    private final Set<UUID> online;

    public NetworkPlayerRegistry() {
        this.namesByUuid = new ConcurrentHashMap<>();
        this.uuidsByLowerName = new ConcurrentHashMap<>();
        this.online = ConcurrentHashMap.newKeySet();
    }

    public void upsertKnown(UUID uuid, String name) {
        this.namesByUuid.put(uuid, name);
        this.uuidsByLowerName.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    public void setOnline(UUID uuid, String name, boolean online) {
        upsertKnown(uuid, name);
        if (online) {
            this.online.add(uuid);
            return;
        }
        this.online.remove(uuid);
    }

    public void handleJoin(Player player) {
        setOnline(player.getUniqueId(), player.getName(), true);
    }

    public void handleQuit(Player player) {
        setOnline(player.getUniqueId(), player.getName(), false);
    }

    public UUID findOnlineByName(String name) {
        UUID uuid = this.uuidsByLowerName.get(name.toLowerCase(Locale.ROOT));
        if (uuid == null) {
            return null;
        }
        return this.online.contains(uuid) ? uuid : null;
    }

    public String nameOf(UUID uuid) {
        return this.namesByUuid.get(uuid);
    }

    public boolean isOnline(UUID uuid) {
        return this.online.contains(uuid);
    }

    public List<String> onlineNamesStartingWith(String input) {
        String current = input.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (UUID uuid : this.online) {
            String name = this.namesByUuid.get(uuid);
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(current)) {
                names.add(name);
            }
        }
        names.sort(Comparator.comparing(String::toLowerCase));
        return names;
    }

    public void replaceKnownPlayers(Collection<PlayerRecord> records) {
        this.namesByUuid.clear();
        this.uuidsByLowerName.clear();
        for (PlayerRecord record : records) {
            upsertKnown(record.uuid(), record.name());
        }
    }

    public void replaceOnline(Map<UUID, String> onlinePlayers) {
        this.online.clear();
        for (Map.Entry<UUID, String> entry : onlinePlayers.entrySet()) {
            setOnline(entry.getKey(), entry.getValue(), true);
        }
    }

    public record PlayerRecord(UUID uuid, String name) {
    }
}
