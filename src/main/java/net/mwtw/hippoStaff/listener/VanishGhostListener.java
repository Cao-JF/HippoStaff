package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.vanish.VanishManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;

public final class VanishGhostListener implements Listener {
    private final VanishManager vanishManager;

    public VanishGhostListener(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!this.vanishManager.isProjectilePassThroughEnabled()) {
            return;
        }
        if (isVanishedPlayer(event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!this.vanishManager.isProjectilePassThroughEnabled()) {
            return;
        }
        if (!(event.getDamager() instanceof Projectile)) {
            return;
        }
        if (isVanishedPlayer(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onVehicleCollision(VehicleEntityCollisionEvent event) {
        if (!this.vanishManager.isZeroCollisionEnabled()) {
            return;
        }
        if (isVanishedPlayer(event.getEntity()) || hasVanishedPassenger(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    private boolean isVanishedPlayer(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        return this.vanishManager.isVanished(player.getUniqueId());
    }

    private boolean hasVanishedPassenger(Vehicle vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (isVanishedPlayer(passenger)) {
                return true;
            }
            if (passenger instanceof Vehicle nested && hasVanishedPassenger(nested)) {
                return true;
            }
        }
        return false;
    }
}
