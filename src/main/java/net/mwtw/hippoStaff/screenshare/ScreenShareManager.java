package net.mwtw.hippoStaff.screenshare;

import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScreenShareManager {
    private final Core plugin;
    private final MessageService messageService;
    private final Set<UUID> frozen;
    private File file;
    private YamlConfiguration data;
    private BukkitTask reminderTask;

    public ScreenShareManager(Core plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
        this.frozen = ConcurrentHashMap.newKeySet();
    }

    public void init() {
        this.file = new File(this.plugin.getDataFolder(), "screenshare-data.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
        for (String raw : this.data.getStringList("frozen")) {
            try {
                this.frozen.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void start() {
        // Re-apply to anyone frozen who is already online (e.g. after a plugin reload).
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isFrozen(player.getUniqueId())) {
                applyFrozenState(player);
            }
        }
        startReminderTask();
    }

    public void shutdown() {
        if (this.reminderTask != null) {
            this.reminderTask.cancel();
            this.reminderTask = null;
        }
        // Clear live effects so a plugin reload does not leave players blind; the persisted
        // frozen set is kept so they remain frozen after a restart / relog.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isFrozen(player.getUniqueId())) {
                clearEffects(player);
            }
        }
        save();
    }

    public boolean isFrozen(UUID uuid) {
        return this.frozen.contains(uuid);
    }

    public void toggle(CommandSender admin, Player target) {
        if (isFrozen(target.getUniqueId())) {
            unfreeze(admin, target);
        } else {
            freeze(admin, target);
        }
    }

    private void freeze(CommandSender admin, Player target) {
        this.frozen.add(target.getUniqueId());
        save();
        applyFrozenState(target);
        this.messageService.send(admin, "screenshare.admin-frozen", null, Map.of("player", target.getName()));
    }

    private void unfreeze(CommandSender admin, Player target) {
        this.frozen.remove(target.getUniqueId());
        save();
        clearEffects(target);
        this.messageService.send(target, "screenshare.released");
        this.messageService.send(admin, "screenshare.admin-released", null, Map.of("player", target.getName()));
    }

    public void handleJoin(Player player) {
        // Anti-evasion: a player who logs out while frozen stays frozen on rejoin.
        if (!isFrozen(player.getUniqueId())) {
            return;
        }
        // Apply one tick later so the client is fully ready to receive the title/effects.
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline() && isFrozen(player.getUniqueId())) {
                applyFrozenState(player);
            }
        });
    }

    private void applyFrozenState(Player target) {
        applyBlindness(target);
        showTitle(target);
        this.messageService.send(target, "screenshare.frozen");
        sendReminder(target);
    }

    private void startReminderTask() {
        long intervalSeconds = Math.max(1L, this.plugin.getConfig().getLong("screenshare.reminder-interval-seconds", 5L));
        long intervalTicks = 20L * intervalSeconds;
        this.reminderTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (this.frozen.isEmpty()) {
                return;
            }
            for (UUID uuid : this.frozen) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                // Re-apply each interval so the effects/title naturally lapse if the player
                // is released instead of lingering, while staying refreshed while frozen.
                applyBlindness(player);
                showTitle(player);
                sendReminder(player);
            }
        }, intervalTicks, intervalTicks);
    }

    private void applyBlindness(Player player) {
        if (!this.plugin.getConfig().getBoolean("screenshare.blindness", true)) {
            return;
        }
        long intervalSeconds = Math.max(1L, this.plugin.getConfig().getLong("screenshare.reminder-interval-seconds", 5L));
        int durationTicks = (int) (20L * (intervalSeconds + 3L));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, true, false, false));
    }

    private void showTitle(Player player) {
        long fadeIn = this.plugin.getConfig().getLong("screenshare.title.fade-in-ms", 0L);
        long stay = this.plugin.getConfig().getLong("screenshare.title.stay-ms", 6000L);
        long fadeOut = this.plugin.getConfig().getLong("screenshare.title.fade-out-ms", 500L);
        this.messageService.title(player, "screenshare.title", "screenshare.subtitle", Map.of(), fadeIn, stay, fadeOut);
    }

    private void sendReminder(Player player) {
        this.messageService.send(player, "screenshare.reminder");
    }

    private void clearEffects(Player player) {
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.clearTitle();
    }

    private void save() {
        if (this.data == null) {
            return;
        }
        List<String> serialized = new ArrayList<>(this.frozen.size());
        for (UUID uuid : this.frozen) {
            serialized.add(uuid.toString());
        }
        this.data.set("frozen", serialized);
        try {
            this.data.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save screenshare-data.yml: " + exception.getMessage());
        }
    }
}
