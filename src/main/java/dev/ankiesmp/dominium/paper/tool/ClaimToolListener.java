package dev.ankiesmp.dominium.paper.tool;

import dev.ankiesmp.dominium.api.ClaimBlockHolderType;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;
import java.util.Optional;

/**
 * Verwerkt shovel-interacties. Alle DB-werk (balance lookup, create,
 * resize) delegeert de listener naar de application services — nooit
 * hier direct.
 */
public final class ClaimToolListener implements Listener {

    private final ClaimTool tool;
    private final SelectionState selections;
    private final ClaimIndex claimIndex;
    private final ClaimBlockLedger ledger;

    public ClaimToolListener(ClaimTool tool, SelectionState selections,
                             ClaimIndex claimIndex, ClaimBlockLedger ledger) {
        this.tool = Objects.requireNonNull(tool);
        this.selections = Objects.requireNonNull(selections);
        this.claimIndex = Objects.requireNonNull(claimIndex);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!tool.isTool(event.getItem())) return;
        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean sneaking = player.isSneaking();

        // Sneak+right = cycle mode (blok en air beide toegestaan).
        if (sneaking && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            ShovelMode next = tool.nextMode(tool.readMode(event.getItem()));
            tool.writeMode(event.getItem(), next);
            selections.clear(player.getUniqueId());
            player.sendActionBar(Component.text("Shovel mode: " + next, NamedTextColor.YELLOW));
            return;
        }

        // Sneak+left = cancel selection.
        if (sneaking && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            selections.clear(player.getUniqueId());
            player.sendActionBar(Component.text("Selection cancelled.", NamedTextColor.GRAY));
            return;
        }

        // Inspect (left-click block).
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block b = event.getClickedBlock();
            if (b == null) return;
            inspect(player, b.getLocation());
            return;
        }

        // Set corner (right-click block).
        if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block b = event.getClickedBlock();
            if (b == null) return;
            ShovelMode mode = tool.readMode(event.getItem());
            if (mode == ShovelMode.INSPECT) {
                inspect(player, b.getLocation());
                return;
            }
            handleCorner(player, mode, b.getLocation());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selections.clear(event.getPlayer().getUniqueId());
    }

    private void handleCorner(Player player, ShovelMode mode, Location loc) {
        var world = new dev.ankiesmp.dominium.core.common.WorldRef(loc.getWorld().getUID());
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        Optional<SelectionState.Selection> currentOpt = selections.current(player.getUniqueId());

        if (currentOpt.isEmpty() || !currentOpt.get().world().equals(world)
                || currentOpt.get().mode() != mode || currentOpt.get().second().isPresent()) {
            selections.beginOrReplace(player.getUniqueId(), world, mode, x, z);
            player.sendMessage(Component.text(
                    "Corner A set at (" + x + ", " + z + "). Right-click a second block for corner B.",
                    NamedTextColor.GOLD));
            return;
        }

        selections.setSecond(player.getUniqueId(), world, x, z);
        ClaimRectangle rect = selections.current(player.getUniqueId())
                .flatMap(SelectionState.Selection::asRectangle)
                .orElseThrow();
        long cost = rect.cost();
        long balance = ledger.balanceOrZero(HolderKey.of(ClaimBlockHolderType.PLAYER, player.getUniqueId())).balance();
        player.sendMessage(Component.text(
                "Preview: " + rect.width() + " x " + rect.depth()
                        + " = " + cost + " claim blocks. Balance: " + balance + ".",
                NamedTextColor.YELLOW));
        player.sendMessage(Component.text(
                "Confirm with /claim confirm  ·  cancel with sneak+left-click.",
                NamedTextColor.GRAY));
    }

    private void inspect(Player player, Location loc) {
        var world = new dev.ankiesmp.dominium.core.common.WorldRef(loc.getWorld().getUID());
        Optional<Claim> found = claimIndex.containing(world, loc.getBlockX(), loc.getBlockZ());
        if (found.isEmpty()) {
            player.sendMessage(Component.text("Wilderness — no claim here.", NamedTextColor.GRAY));
            return;
        }
        Claim c = found.get();
        player.sendMessage(Component.text(
                "Claim " + c.id() + " (" + c.owner() + ")  "
                        + c.rect().width() + "x" + c.rect().depth(),
                NamedTextColor.AQUA));
    }
}
