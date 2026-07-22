package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.block.BlockState;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Bewaakt {@link Flag#CONTAINER} bij het openen van een inventory.
 * Alle storage-holders (chests, barrels, shulker boxes, furnaces,
 * hoppers, droppers, dispensers, decorated pots, brewing stands,
 * crafters, e.d.) gaan hierdoor — Bukkit modelleert dat via
 * {@link InventoryHolder}. Ender chests en workbenches zijn géén
 * container-holder en blijven vrij.
 */
public final class ContainerListener implements Listener {

    private final ProtectionGuard guard;

    public ContainerListener(ProtectionGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockState state)) return;

        AccessDecision d = guard.checkBlock(player, state.getBlock(), Flag.CONTAINER);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(player, Flag.CONTAINER);
        }
    }
}
