package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Vertaalt {@link PlayerInteractEvent} naar de juiste {@link Flag} op
 * basis van blocktype. Containers worden apart bewaakt door
 * {@link ContainerListener}; hier vangen we door/gate/trapdoor,
 * button/lever/pressure plate en flint &amp; steel af.
 */
public final class InteractListener implements Listener {

    private final ProtectionGuard guard;

    public InteractListener(ProtectionGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Material type = block.getType();

        Flag flag = classify(type, event.getAction());
        if (flag == null) return;

        AccessDecision d = guard.checkBlock(event.getPlayer(), block, flag);
        if (d.denied()) {
            event.setCancelled(true);
            if (event.getAction() != Action.PHYSICAL) {
                guard.notifyDenied(event.getPlayer(), flag);
            }
        }
    }

    private static Flag classify(Material type, Action action) {
        if (Tag.DOORS.isTagged(type)) return Flag.DOOR;
        if (Tag.TRAPDOORS.isTagged(type)) return Flag.TRAPDOOR;
        if (Tag.FENCE_GATES.isTagged(type)) return Flag.GATE;
        if (Tag.BUTTONS.isTagged(type)) return Flag.BUTTON;
        if (type == Material.LEVER) return Flag.LEVER;
        if (Tag.PRESSURE_PLATES.isTagged(type)) return Flag.PRESSURE_PLATE;
        if (type == Material.FLINT_AND_STEEL) return Flag.FLINT_AND_STEEL;
        if (type == Material.BONE_MEAL) return Flag.BONE_MEAL;
        if (type == Material.SHEARS) return Flag.SHEARS;
        if (type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL) return Flag.ANVIL_ENCHANT;
        if (type == Material.BEACON) return Flag.BEACON;
        if (Tag.BEDS.isTagged(type)) return Flag.BED_RESPAWN;
        if (action == Action.PHYSICAL && Tag.CROPS.isTagged(type)) return Flag.TRAMPLE;
        return null;
    }
}
