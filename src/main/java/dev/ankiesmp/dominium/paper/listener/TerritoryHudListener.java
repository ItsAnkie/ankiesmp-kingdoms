package dev.ankiesmp.dominium.paper.listener;

import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.protection.Audience;
import dev.ankiesmp.dominium.core.territory.TerritoryContext;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

/**
 * Rest-listener: No Access movement enforcement + cache-invalidation
 * bij quit/world change. De actionbar-HUD zelf wordt onderhouden door
 * {@code TerritoryHudTask} (constante refresh).
 */
public final class TerritoryHudListener implements Listener {

    private final TerritoryContextCache cache;

    public TerritoryHudListener(TerritoryContextCache cache) {
        this.cache = Objects.requireNonNull(cache);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        if (to == null) return;
        int fx = from.getBlockX(), fz = from.getBlockZ();
        int tx = to.getBlockX(),   tz = to.getBlockZ();
        if (fx == tx && fz == tz && from.getWorld().equals(to.getWorld())) return;

        Player player = event.getPlayer();
        WorldRef world = new WorldRef(to.getWorld().getUID());
        TerritoryContext ctx = cache.contextAt(world, tx, tz, player.getUniqueId());
        if (ctx.inClaim() && ctx.noAccess() && ctx.audience() == Audience.PUBLIC) {
            var fromLoc = from.clone();
            fromLoc.setYaw(to.getYaw());
            fromLoc.setPitch(to.getPitch());
            event.setTo(fromLoc);
            player.sendActionBar(Component.text("No Access", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        var to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();
        WorldRef world = new WorldRef(to.getWorld().getUID());
        TerritoryContext ctx = cache.contextAt(world, to.getBlockX(), to.getBlockZ(),
                player.getUniqueId());
        if (ctx.inClaim() && ctx.noAccess() && ctx.audience() == Audience.PUBLIC) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("No Access", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        cache.invalidatePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.invalidatePlayer(event.getPlayer().getUniqueId());
    }
}
