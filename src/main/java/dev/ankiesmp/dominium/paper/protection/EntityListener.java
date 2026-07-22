package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Bescherming van entities die makkelijk misbruikt worden voor grief:
 * item frames, paintings, minecarts, boten en direct interactie met
 * villagers/dieren.
 */
public final class EntityListener implements Listener {

    private final ProtectionGuard guard;

    public EntityListener(ProtectionGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player attacker = resolvePlayer(event.getRemover());
        AccessDecision d = guard.check(attacker,
                event.getEntity().getLocation(), Flag.PLACE_ENTITIES);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(attacker, Flag.PLACE_ENTITIES);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player attacker = resolvePlayer(event.getAttacker());
        AccessDecision d = guard.check(attacker,
                event.getVehicle().getLocation(), Flag.VEHICLE);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(attacker, Flag.VEHICLE);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        Flag flag = null;
        if (target.getType().name().contains("VILLAGER")) flag = Flag.VILLAGER_TRADE;
        else if (target.getType().name().contains("MINECART")
                || target.getType().name().contains("BOAT")) flag = Flag.VEHICLE;
        else if (target instanceof org.bukkit.entity.ItemFrame
                || target instanceof org.bukkit.entity.ArmorStand
                || target instanceof org.bukkit.entity.Painting) flag = Flag.PLACE_ENTITIES;
        else if (target instanceof org.bukkit.entity.Animals) flag = Flag.ANIMAL_INTERACT;
        if (flag == null) return;

        AccessDecision d = guard.check(event.getPlayer(),
                target.getLocation(), flag);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(event.getPlayer(), flag);
        }
    }

    private static Player resolvePlayer(Entity source) {
        if (source instanceof Player p) return p;
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) return p;
        }
        return null;
    }
}
