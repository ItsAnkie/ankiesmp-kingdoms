package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Beschermt tegen PvP en schade aan gedomesticeerde dieren binnen een
 * claim. Fase 2 gebruikt drie flags:
 * <ul>
 *     <li>{@link Flag#PVP} — speler → speler</li>
 *     <li>{@link Flag#PET_PROTECT} — speler → getemd dier</li>
 *     <li>{@link Flag#ANIMAL_DAMAGE} — speler → passief dier</li>
 * </ul>
 */
public final class PvPListener implements Listener {

    private final ProtectionGuard guard;

    public PvPListener(ProtectionGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) return;

        Entity victim = event.getEntity();
        Flag flag;
        if (victim instanceof Player) {
            flag = Flag.PVP;
        } else if (victim instanceof Tameable tameable && tameable.getOwner() != null
                && !attacker.getUniqueId().equals(tameable.getOwner().getUniqueId())) {
            flag = Flag.PET_PROTECT;
        } else if (victim instanceof Animals) {
            flag = Flag.ANIMAL_DAMAGE;
        } else {
            return;
        }

        AccessDecision d = guard.check(attacker, victim.getLocation(), flag);
        if (d.denied()) {
            event.setCancelled(true);
            guard.notifyDenied(attacker, flag);
        }
    }

    private static Player resolvePlayer(Entity source) {
        if (source instanceof Player player) return player;
        if (source instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }
}
