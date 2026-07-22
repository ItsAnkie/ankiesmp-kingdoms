package dev.ankiesmp.dominium.paper.listener;

import dev.ankiesmp.dominium.core.activity.ActivityTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/**
 * Voedt de {@link ActivityTracker} vanuit Bukkit-events. Alle handlers
 * zijn allocation-arm en doen géén databasewerk.
 */
public final class ActivityListener implements Listener {

    private final ActivityTracker tracker;

    public ActivityListener(ActivityTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) return;
        // Alleen block-level movement telt — kop-draaien is niet actief.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof org.bukkit.entity.Player p) {
            tracker.recordActivity(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        tracker.recordActivity(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tracker.onJoin(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Quit-flush wordt door de scheduler-task afgehandeld (heeft dbExecutor nodig).
        // Hier alleen sessie afsluiten via drain in de scheduler.
    }
}
