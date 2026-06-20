package net.mwtw.hippoStaff.listener;

import net.mwtw.hippoStaff.message.PrivateMessageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PrivateMessagePresenceListener implements Listener {
    private final PrivateMessageManager manager;

    public PrivateMessagePresenceListener(PrivateMessageManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.manager.handleQuit(event.getPlayer());
    }
}
