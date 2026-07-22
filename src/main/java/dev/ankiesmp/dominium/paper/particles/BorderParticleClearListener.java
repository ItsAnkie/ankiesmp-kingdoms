package dev.ankiesmp.dominium.paper.particles;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/** Wist border-render-state onmiddellijk bij quit/teleport/world change. */
public final class BorderParticleClearListener implements Listener {

    private final ClaimBorderParticleTask task;

    public BorderParticleClearListener(ClaimBorderParticleTask task) {
        this.task = Objects.requireNonNull(task);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        task.clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        task.clearPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        task.clearPlayer(event.getPlayer().getUniqueId());
    }
}
