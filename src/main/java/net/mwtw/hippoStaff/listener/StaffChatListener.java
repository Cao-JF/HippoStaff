package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.staffchat.StaffChatManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StaffChatListener implements Listener {
    private final StaffChatManager staffChatManager;

    public StaffChatListener(StaffChatManager staffChatManager) {
        this.staffChatManager = staffChatManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onStaffPrefixedChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!this.staffChatManager.canUse(player)) {
            return;
        }

        String message = event.getMessage();
        String triggerPrefix = this.staffChatManager.getTriggerPrefix();
        if (!triggerPrefix.isBlank() && message.startsWith(triggerPrefix) && message.length() > triggerPrefix.length()) {
            String stripped = message.substring(triggerPrefix.length()).trim();
            if (stripped.isEmpty()) {
                return;
            }
            event.setCancelled(true);
            this.staffChatManager.sendFromPlayer(player, stripped, true);
            return;
        }

        if (this.staffChatManager.isToggled(player.getUniqueId())) {
            event.setCancelled(true);
            this.staffChatManager.sendFromPlayer(player, message, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.staffChatManager.clear(event.getPlayer().getUniqueId());
    }
}
