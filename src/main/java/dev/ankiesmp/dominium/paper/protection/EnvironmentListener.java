package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.BlockIterator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cross-border en environment-bescherming: pistons, vloeistoffen, vuur,
 * explosies, dispensers, en block-veranderende entities (enderman,
 * wither).
 *
 * <p>Strategie: gebruik geen speler-actor (er is er meestal geen).
 * Twee blocks krijgen elk hun eigen claim-lookup; als de eigenaars
 * verschillen én de bestemming beschermd is voor de betreffende flag,
 * cancellen we (of filteren we de blocklist).
 */
public final class EnvironmentListener implements Listener {

    private final ProtectionGuard guard;
    private final ClaimIndex index;

    public EnvironmentListener(ProtectionGuard guard, ClaimIndex index) {
        this.guard = guard;
        this.index = index;
    }

    // ---------- Piston push / pull ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        Optional<Claim> pistonClaim = claimAt(piston);
        for (Block moved : event.getBlocks()) {
            Block dest = moved.getRelative(event.getDirection());
            if (crossesOwnershipDenied(pistonClaim, dest, Flag.PISTON)) {
                event.setCancelled(true);
                return;
            }
            if (crossesOwnershipDenied(pistonClaim, moved, Flag.PISTON)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        Optional<Claim> pistonClaim = claimAt(piston);
        for (Block moved : event.getBlocks()) {
            if (crossesOwnershipDenied(pistonClaim, moved, Flag.PISTON)) {
                event.setCancelled(true);
                return;
            }
            Block dest = moved.getRelative(event.getDirection());
            if (crossesOwnershipDenied(pistonClaim, dest, Flag.PISTON)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ---------- Liquid flow ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Optional<Claim> source = claimAt(event.getBlock());
        if (crossesOwnershipDenied(source, event.getToBlock(), Flag.LIQUID_FLOW)) {
            event.setCancelled(true);
        }
    }

    // ---------- Fire spread & ignite ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) return; // handled by BuildListener/InteractListener
        AccessDecision d = guard.check((java.util.UUID) null, event.getBlock().getLocation(), Flag.FIRE_SPREAD);
        if (d.denied()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != org.bukkit.Material.FIRE) return;
        AccessDecision d = guard.check((java.util.UUID) null, event.getBlock().getLocation(), Flag.FIRE_SPREAD);
        if (d.denied()) event.setCancelled(true);
    }

    // ---------- Explosions ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Flag flag = explosionFlag(event.getEntity());
        filterBlocks(event.blockList(), flag);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterBlocks(event.blockList(), Flag.EXPLOSION_OTHER);
    }

    private void filterBlocks(List<Block> blocks, Flag flag) {
        blocks.removeIf(block -> guard.check((java.util.UUID) null,
                block.getLocation(), flag).denied());
    }

    // ---------- Dispenser dispense ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Optional<Claim> source = claimAt(event.getBlock());
        // Simpele proxy voor de gedispenseerde locatie: één blok voorwaarts vanaf de dispenser.
        Block front = tryFrontBlock(event.getBlock());
        if (front == null) return;
        if (crossesOwnershipDenied(source, front, Flag.DISPENSER)) {
            event.setCancelled(true);
        }
    }

    // ---------- Enderman/wither/farmland trample ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity e = event.getEntity();
        // Farmland trample
        if (event.getBlock().getType() == org.bukkit.Material.FARMLAND) {
            AccessDecision d = guard.check((java.util.UUID) null,
                    event.getBlock().getLocation(), Flag.TRAMPLE);
            if (d.denied()) { event.setCancelled(true); return; }
        }
        // Mob griefing (enderman, wither, silverfish, ravager, zombie doors etc.)
        if (!(e instanceof org.bukkit.entity.Player)) {
            AccessDecision d = guard.check((java.util.UUID) null,
                    event.getBlock().getLocation(), Flag.MOB_GRIEFING);
            if (d.denied()) event.setCancelled(true);
        }
    }

    // ---------- helpers ----------

    private Optional<Claim> claimAt(Block block) {
        WorldRef world = new WorldRef(block.getWorld().getUID());
        return index.containing(world, block.getX(), block.getZ());
    }

    /**
     * True als beweging van bron-eigendom naar {@code destination} een
     * andere eigenaar raakt én de destination-flag DENY oplevert.
     */
    private boolean crossesOwnershipDenied(Optional<Claim> sourceClaim, Block destination, Flag flag) {
        Optional<Claim> destClaim = claimAt(destination);
        if (destClaim.isEmpty()) return false; // beweging naar wilderness — ok
        if (sourceClaim.isPresent() && sourceClaim.get().id().equals(destClaim.get().id())) {
            return false; // zelfde claim
        }
        // Zelfde owner in verschillende claims? Sta ook toe.
        if (sourceClaim.isPresent()
                && Objects.equals(sourceClaim.get().owner(), destClaim.get().owner())) {
            return false;
        }
        AccessDecision d = guard.service().checkInClaim(destClaim.get(), null, flag);
        return d.denied();
    }

    private static Flag explosionFlag(Entity entity) {
        if (entity instanceof TNTPrimed) return Flag.EXPLOSION_TNT;
        if (entity instanceof Creeper) return Flag.EXPLOSION_CREEPER;
        if (entity instanceof Wither || entity instanceof EnderDragon) return Flag.MOB_GRIEFING;
        return Flag.EXPLOSION_OTHER;
    }

    private static Block tryFrontBlock(Block dispenser) {
        try {
            BlockIterator it = new BlockIterator(dispenser.getWorld(),
                    dispenser.getLocation().toVector().add(new org.bukkit.util.Vector(0.5, 0.5, 0.5)),
                    dispenser.getState().getBlockData() instanceof org.bukkit.block.data.Directional dir
                            ? dir.getFacing().getDirection()
                            : new org.bukkit.util.Vector(0, 1, 0),
                    0.0, 1);
            return it.hasNext() ? it.next() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
