package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.protection.AccessDecision;
import dev.ankiesmp.dominium.core.protection.Flag;
import dev.ankiesmp.dominium.core.protection.ProtectionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Dun laagje boven {@link ProtectionService} zodat listeners kort en
 * uniform blijven. Levert helper-methoden voor "cancel if denied" en
 * een consistente feedback-actionbar naar de speler.
 */
public final class ProtectionGuard {

    private final ProtectionService protection;

    public ProtectionGuard(ProtectionService protection) {
        this.protection = Objects.requireNonNull(protection);
    }

    public AccessDecision check(Player player, Location loc, Flag flag) {
        return check(player == null ? null : player.getUniqueId(), loc, flag);
    }

    public AccessDecision check(UUID actorId, Location loc, Flag flag) {
        return protection.check(worldOf(loc.getWorld()),
                loc.getBlockX(), loc.getBlockZ(), actorId, flag);
    }

    public AccessDecision checkBlock(Player player, org.bukkit.block.Block block, Flag flag) {
        return protection.check(worldOf(block.getWorld()),
                block.getX(), block.getZ(),
                player == null ? null : player.getUniqueId(), flag);
    }

    public void notifyDenied(Player player, Flag flag) {
        if (player == null) return;
        player.sendActionBar(Component.text("Blocked: " + flag.name().toLowerCase().replace('_', ' '),
                NamedTextColor.RED));
    }

    public ProtectionService service() { return protection; }

    private static WorldRef worldOf(World world) {
        return new WorldRef(world.getUID());
    }
}
