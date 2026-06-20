package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.message.MessageService;
import net.mwtw.hippoStaff.vanish.PickupSettingsManager;
import net.mwtw.hippoStaff.vanish.VanishManager;
import net.mwtw.hippoStaff.vanish.rules.RuleManager;
import net.mwtw.hippoStaff.vanish.rules.VanishRule;
import org.bukkit.entity.Entity;
import org.bukkit.GameEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;

import java.util.Set;

public final class VanishRuleListener implements Listener {
    private static final Set<String> PHYSICAL_GAME_EVENTS = Set.of(
            "step",
            "hit_ground",
            "splash",
            "swim",
            "flap"
    );

    private final VanishManager vanishManager;
    private final RuleManager ruleManager;
    private final PickupSettingsManager pickupSettingsManager;
    private final MessageService messageService;

    public VanishRuleListener(VanishManager vanishManager, RuleManager ruleManager, PickupSettingsManager pickupSettingsManager, MessageService messageService) {
        this.vanishManager = vanishManager;
        this.ruleManager = ruleManager;
        this.pickupSettingsManager = pickupSettingsManager;
        this.messageService = messageService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), VanishRule.CAN_BREAK_BLOCKS, "vanish.rules.blocked.break")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), VanishRule.CAN_PLACE_BLOCKS, "vanish.rules.blocked.place")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            if (isPhysicalTriggerBlocked(player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getClickedBlock() != null && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            if (blocked(player, VanishRule.CAN_INTERACT, "vanish.rules.blocked.interact")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = extractPlayerAttacker(event.getDamager());
        if (attacker != null && blocked(attacker, VanishRule.CAN_HIT_ENTITIES, "vanish.rules.blocked.hit")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isVanished(player)) {
            return;
        }
        if (this.ruleManager.isEnabled(player, VanishRule.CAN_PICKUP_ITEMS)) {
            return;
        }
        if (this.pickupSettingsManager.isEnabled(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        this.messageService.send(player, "vanish.rules.blocked.pickup");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (blocked(event.getPlayer(), VanishRule.CAN_DROP_ITEMS, "vanish.rules.blocked.drop")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isPhysicalTriggerBlocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!isPhysicalTriggerBlocked(player)) {
            return;
        }

        GameEvent gameEvent = event.getEvent();
        String key = gameEvent.getKey().getKey();
        if (PHYSICAL_GAME_EVENTS.contains(key)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isVanished(player) || this.ruleManager.isEnabled(player, VanishRule.CAN_CHAT)) {
            return;
        }

        String plain = event.getMessage();
        if (!this.ruleManager.confirmChat(player.getUniqueId(), plain)) {
            event.setCancelled(true);
            this.messageService.send(player, "vanish.rules.blocked.chat-confirm");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        if (blocked(player, VanishRule.CAN_THROW, "vanish.rules.blocked.throw")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (!(target instanceof Player player)) {
            return;
        }
        if (!isVanished(player)) {
            return;
        }
        if (this.ruleManager.isEnabled(player, VanishRule.MOB_TARGETING)) {
            return;
        }
        event.setCancelled(true);
        event.setTarget(null);
    }

    private boolean blocked(Player player, VanishRule rule, String messageKey) {
        if (!isVanished(player)) {
            return false;
        }
        if (this.ruleManager.isEnabled(player, rule)) {
            return false;
        }
        this.messageService.send(player, messageKey);
        return true;
    }

    private boolean isVanished(Player player) {
        return this.vanishManager.isVanished(player.getUniqueId());
    }

    private boolean isPhysicalTriggerBlocked(Player player) {
        return isVanished(player) && !this.ruleManager.isEnabled(player, VanishRule.CAN_TRIGGER_PHYSICAL);
    }

    private Player extractPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
