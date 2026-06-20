package net.mwtw.hippoStaff.listener;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import net.mwtw.hippoStaff.vanish.VanishManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;

public final class ServerListVisibilityListener implements Listener {
    private final VanishManager vanishManager;

    public ServerListVisibilityListener(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(PaperServerListPingEvent event) {
        if (!this.vanishManager.isServerListHidingEnabled()) {
            return;
        }

        int adjusted = Math.max(0, event.getNumPlayers() - this.vanishManager.getVanishedOnlineCount());
        event.setNumPlayers(adjusted);

        Set<UUID> vanished = this.vanishManager.getVanishedPlayersSnapshot();
        event.getListedPlayers().removeIf(info -> vanished.contains(info.id()));
    }
}
