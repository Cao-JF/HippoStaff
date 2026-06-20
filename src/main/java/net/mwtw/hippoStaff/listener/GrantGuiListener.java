package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.grant.gui.GrantGuiService;
import net.mwtw.hippoStaff.grant.gui.GrantGuiService.GrantHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GrantGuiListener implements Listener {
    private final GrantGuiService guiService;

    public GrantGuiListener(GrantGuiService guiService) {
        this.guiService = guiService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (!(rawHolder instanceof GrantHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        switch (holder.screen()) {
            case MAIN -> this.guiService.handleMainClick(player, holder, slot);
            case GROUP_SELECT -> this.guiService.handleGroupClick(player, holder, slot, clicked);
            case DURATION_SELECT -> this.guiService.handleDurationClick(player, holder, slot, clicked);
            case REVOKE_SELECT -> this.guiService.handleRevokeClick(player, holder, slot, clicked);
            case ACTIVE_LIST, HISTORY -> this.guiService.handleBackOnly(player, holder, slot);
        }
    }
}
