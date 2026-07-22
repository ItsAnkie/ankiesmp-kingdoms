package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

/**
 * Bescherming voor bouwen en breken door spelers. Cross-border pistons,
 * TNT etc. worden apart afgehandeld in {@link EnvironmentListener}.
 */
public final class BuildListener implements Listener {

    private final ProtectionGuard guard;

    public BuildListener(ProtectionGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        AccessDecision d = guard.checkBlock(event.getPlayer(), event.getBlock(), Flag.BREAK);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(event.getPlayer(), Flag.BREAK);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        AccessDecision d = guard.checkBlock(event.getPlayer(), event.getBlockPlaced(), Flag.BUILD);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(event.getPlayer(), Flag.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        AccessDecision d = guard.check(event.getPlayer(),
                event.getBlockClicked().getRelative(event.getBlockFace()).getLocation(), Flag.BUCKET);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(event.getPlayer(), Flag.BUCKET);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        AccessDecision d = guard.checkBlock(event.getPlayer(), event.getBlockClicked(), Flag.BUCKET);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(event.getPlayer(), Flag.BUCKET);
        }
    }
}
