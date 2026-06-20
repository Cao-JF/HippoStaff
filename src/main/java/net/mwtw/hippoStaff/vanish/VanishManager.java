package net.mwtw.hippoStaff.vanish;

import net.mwtw.hippoStaff.Core;
import net.mwtw.hippoStaff.hook.VoiceChatSupport;
import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.storage.VanishStorage;
import net.mwtw.hippoStaff.sync.RedisSyncService;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishManager {
    private final Core plugin;
    private final VanishStorage storage;
    private final MessageService messageService;
    private final Set<UUID> vanishedPlayers;
    private final Map<UUID, Boolean> collidableSnapshot;
    private final Map<UUID, BossBar> activeBossBars;
    private VoiceChatSupport voiceChatSupport;
    private RedisSyncService redisSyncService;
    private BukkitTask actionbarTask;
    private BukkitTask syncTask;
    private BukkitTask bossbarTask;

    public VanishManager(Core plugin, VanishStorage storage, MessageService messageService) {
        this.plugin = plugin;
        this.storage = storage;
        this.messageService = messageService;
        this.vanishedPlayers = ConcurrentHashMap.newKeySet();
        this.collidableSnapshot = new ConcurrentHashMap<>();
        this.activeBossBars = new ConcurrentHashMap<>();
    }

    public void start() {
        loadInitialStates();
        applyOnlineStates();
        refreshAllVisibility();
        startActionbarTask();
        startSyncTask();
        startBossbarTask();
    }

    public void shutdown() {
        if (this.actionbarTask != null) {
            this.actionbarTask.cancel();
        }
        if (this.syncTask != null) {
            this.syncTask.cancel();
        }
        if (this.bossbarTask != null) {
            this.bossbarTask.cancel();
        }
        if (this.voiceChatSupport != null) {
            this.voiceChatSupport.shutdown();
        }
        clearAllBossBars();
        clearGhostEffectsForOnlinePlayers();
        restoreAllCollisionStates();
    }

    public void reloadRuntime() {
        if (this.actionbarTask != null) {
            this.actionbarTask.cancel();
            this.actionbarTask = null;
        }
        if (this.syncTask != null) {
            this.syncTask.cancel();
            this.syncTask = null;
        }
        if (this.bossbarTask != null) {
            this.bossbarTask.cancel();
            this.bossbarTask = null;
        }
        applyOnlineStates();
        refreshAllVisibility();
        startActionbarTask();
        startSyncTask();
        startBossbarTask();
    }

    public void setRedisSyncService(RedisSyncService redisSyncService) {
        this.redisSyncService = redisSyncService;
    }

    public void setVoiceChatSupport(VoiceChatSupport voiceChatSupport) {
        this.voiceChatSupport = voiceChatSupport;
    }

    public boolean isVanished(UUID uuid) {
        return this.vanishedPlayers.contains(uuid);
    }

    public Set<UUID> getVanishedPlayersSnapshot() {
        return new HashSet<>(this.vanishedPlayers);
    }

    public int getVanishedOnlineCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isVanished(player.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    public boolean isZeroCollisionEnabled() {
        return this.plugin.getConfig().getBoolean("vanish.zero-collision.enabled", true);
    }

    public boolean isProjectilePassThroughEnabled() {
        return this.plugin.getConfig().getBoolean("vanish.projectile-pass-through.enabled", true);
    }

    public boolean isServerListHidingEnabled() {
        return this.plugin.getConfig().getBoolean("vanish.server-list-hiding.enabled", true);
    }

    public void toggle(Player player) {
        setVanished(player, !isVanished(player.getUniqueId()), true);
    }

    public void setVanished(Player player, boolean vanished, boolean notifyPlayer) {
        UUID uuid = player.getUniqueId();
        if (vanished) {
            this.vanishedPlayers.add(uuid);
            this.storage.set(uuid, true);
            if (notifyPlayer) {
                this.messageService.send(player, "vanish.enabled");
            }
        } else {
            this.vanishedPlayers.remove(uuid);
            this.storage.set(uuid, false);
            if (notifyPlayer) {
                this.messageService.send(player, "vanish.disabled");
            }
        }
        if (this.redisSyncService != null) {
            this.redisSyncService.publish(uuid, vanished);
        }
        if (this.voiceChatSupport != null) {
            this.voiceChatSupport.apply(player, vanished);
        }
        applyCollisionState(player, vanished);
        applyGhostEffectState(player, vanished);
        applyBossbarState(player, vanished);
        refreshAllVisibility();
    }

    public void applyExternalState(UUID uuid, boolean vanished) {
        if (vanished) {
            this.vanishedPlayers.add(uuid);
        } else {
            this.vanishedPlayers.remove(uuid);
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && this.voiceChatSupport != null) {
            this.voiceChatSupport.apply(player, vanished);
        }
        if (player != null) {
            applyCollisionState(player, vanished);
            applyGhostEffectState(player, vanished);
            applyBossbarState(player, vanished);
        }
        refreshAllVisibility();
    }

    public void handleJoin(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            boolean vanished = this.storage.get(player.getUniqueId());
            if (!vanished) {
                return;
            }
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.vanishedPlayers.add(player.getUniqueId());
                if (this.voiceChatSupport != null) {
                    this.voiceChatSupport.apply(player, true);
                }
                applyCollisionState(player, true);
                applyGhostEffectState(player, true);
                applyBossbarState(player, true);
                refreshAllVisibility();
            });
        });
    }

    public void handleQuit(Player player) {
        if (this.voiceChatSupport != null) {
            this.voiceChatSupport.clear(player);
        }
        removeBossbar(player);
        clearGhostEffects(player);
        restoreCollisionState(player);
        refreshAllVisibility();
    }

    private void loadInitialStates() {
        Map<UUID, Boolean> states = this.storage.getAll();
        for (Map.Entry<UUID, Boolean> entry : states.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                this.vanishedPlayers.add(entry.getKey());
            } else {
                this.vanishedPlayers.remove(entry.getKey());
            }
        }
    }

    private void startActionbarTask() {
        if (!this.plugin.getConfig().getBoolean("vanish.actionbar.enabled", true)) {
            return;
        }

        long intervalTicks = Math.max(20L, this.plugin.getConfig().getLong("vanish.actionbar.interval-ticks", 40L));
        String permission = this.plugin.getConfig().getString("vanish.actionbar.permission", "hippostaff.vanish.actionbar");
        this.actionbarTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isVanished(player.getUniqueId())) {
                    continue;
                }
                if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
                    continue;
                }
                this.messageService.actionbar(player, "vanish.actionbar.vanished");
            }
        }, intervalTicks, intervalTicks);
    }

    private void startSyncTask() {
        if (!this.plugin.getConfig().getBoolean("storage.sync.enabled", true)) {
            return;
        }

        long intervalSeconds = Math.max(1L, this.plugin.getConfig().getLong("storage.sync.interval-seconds", 5L));
        this.syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
            Map<UUID, Boolean> states = this.storage.getAll();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                for (Map.Entry<UUID, Boolean> entry : states.entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue())) {
                        this.vanishedPlayers.add(entry.getKey());
                    } else {
                        this.vanishedPlayers.remove(entry.getKey());
                    }
                }
                refreshAllVisibility();
            });
        }, 20L * intervalSeconds, 20L * intervalSeconds);
    }

    private void startBossbarTask() {
        if (!this.plugin.getConfig().getBoolean("vanish.bossbar.enabled", true)) {
            clearAllBossBars();
            return;
        }

        long intervalTicks = Math.max(20L, this.plugin.getConfig().getLong("vanish.bossbar.interval-ticks", 20L));
        this.bossbarTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyBossbarState(player, isVanished(player.getUniqueId()));
            }
        }, intervalTicks, intervalTicks);
    }

    private boolean canSeeVanished(Player viewer, Player target) {
        if (!isVanished(target.getUniqueId())) {
            return true;
        }
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (viewer.hasPermission("hippostaff.vanish.see")) {
            return true;
        }
        return isVanished(viewer.getUniqueId());
    }

    public void refreshAllVisibility() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(target.getUniqueId())) {
                    continue;
                }
                if (canSeeVanished(viewer, target)) {
                    viewer.showPlayer(this.plugin, target);
                } else {
                    viewer.hidePlayer(this.plugin, target);
                }
                if (!isServerListHidingEnabled()) {
                    continue;
                }

                if (isVanished(target.getUniqueId())) {
                    if (viewer.isListed(target)) {
                        viewer.unlistPlayer(target);
                    }
                } else if (viewer.canSee(target) && !viewer.isListed(target)) {
                    viewer.listPlayer(target);
                }
            }
        }
    }

    private void applyOnlineStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean vanished = isVanished(player.getUniqueId());
            if (this.voiceChatSupport != null) {
                this.voiceChatSupport.apply(player, vanished);
            }
            applyCollisionState(player, vanished);
            applyGhostEffectState(player, vanished);
            applyBossbarState(player, vanished);
        }
    }

    private void applyCollisionState(Player player, boolean vanished) {
        if (!isZeroCollisionEnabled()) {
            return;
        }
        if (vanished) {
            this.collidableSnapshot.putIfAbsent(player.getUniqueId(), player.isCollidable());
            player.setCollidable(false);
        } else {
            restoreCollisionState(player);
        }
    }

    private void restoreCollisionState(Player player) {
        Boolean original = this.collidableSnapshot.remove(player.getUniqueId());
        if (original != null) {
            player.setCollidable(original);
        } else {
            player.setCollidable(true);
        }
    }

    private void restoreAllCollisionStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreCollisionState(player);
        }
    }

    private void applyBossbarState(Player player, boolean vanished) {
        if (!this.plugin.getConfig().getBoolean("vanish.bossbar.enabled", true) || !vanished) {
            removeBossbar(player);
            return;
        }

        BossBar.Color color = parseBossbarColor(this.plugin.getConfig().getString("vanish.bossbar.color", "RED"));
        BossBar.Overlay overlay = parseBossbarOverlay(this.plugin.getConfig().getString("vanish.bossbar.overlay", "PROGRESS"));
        float progress = (float) Math.max(0.0, Math.min(1.0, this.plugin.getConfig().getDouble("vanish.bossbar.progress", 1.0)));

        BossBar bar = this.activeBossBars.computeIfAbsent(player.getUniqueId(), ignored ->
                BossBar.bossBar(this.messageService.parse(this.messageService.raw("vanish.bossbar.vanished", player)), progress, color, overlay));
        bar.name(this.messageService.parse(this.messageService.raw("vanish.bossbar.vanished", player)));
        bar.progress(progress);
        bar.color(color);
        bar.overlay(overlay);
        player.showBossBar(bar);
    }

    private void removeBossbar(Player player) {
        BossBar bar = this.activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private void clearAllBossBars() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeBossbar(player);
        }
    }

    private void applyGhostEffectState(Player player, boolean vanished) {
        if (!this.plugin.getConfig().getBoolean("vanish.ghost-effect.enabled", true)) {
            clearGhostEffects(player);
            return;
        }
        if (!vanished) {
            clearGhostEffects(player);
            return;
        }

        boolean ambient = this.plugin.getConfig().getBoolean("vanish.ghost-effect.ambient", true);
        boolean particles = this.plugin.getConfig().getBoolean("vanish.ghost-effect.particles", false);
        boolean icon = this.plugin.getConfig().getBoolean("vanish.ghost-effect.icon", false);
        for (PotionEffectType type : resolveGhostEffectTypes()) {
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, 0, ambient, particles, icon));
        }
    }

    private void clearGhostEffects(Player player) {
        for (PotionEffectType type : resolveGhostEffectTypes()) {
            player.removePotionEffect(type);
        }
    }

    private void clearGhostEffectsForOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clearGhostEffects(player);
        }
    }

    private List<PotionEffectType> resolveGhostEffectTypes() {
        List<String> configured = this.plugin.getConfig().getStringList("vanish.ghost-effect.effects");
        if (configured.isEmpty()) {
            configured = List.of("INVISIBILITY", "NIGHT_VISION");
        }

        List<PotionEffectType> types = new ArrayList<>();
        for (String raw : configured) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalized = raw.toLowerCase(Locale.ROOT);
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(normalized));
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private BossBar.Color parseBossbarColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Color.RED;
        }
        try {
            return BossBar.Color.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay parseBossbarOverlay(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
    }
}
