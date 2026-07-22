package dev.ankiesmp.dominium.paper.listener;

import dev.ankiesmp.dominium.core.ledger.InitialClaimBlockGrant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Roept bij iedere {@link PlayerJoinEvent} de idempotente start-grant aan.
 * De boeking zelf loopt async op de database-executor; alleen de
 * user-facing notificatie wordt weer naar de serverthread teruggeplaatst.
 *
 * <p>Als de service uit staat (starting-balance = 0) of de speler het
 * startsaldo al eerder heeft ontvangen, gebeurt er niets zichtbaars.
 */
public final class InitialGrantListener implements Listener {

    private final Plugin plugin;
    private final InitialClaimBlockGrant service;
    private final Executor dbExecutor;

    public InitialGrantListener(Plugin plugin, InitialClaimBlockGrant service, Executor dbExecutor) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.service = Objects.requireNonNull(service, "service");
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!service.enabled()) return;
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        dbExecutor.execute(() -> {
            InitialClaimBlockGrant.GrantOutcome outcome;
            try {
                outcome = service.attemptFor(playerId);
            } catch (RuntimeException ex) {
                plugin.getSLF4JLogger().warn(
                        "Initial claim block grant for {} failed: {}", playerId, ex.toString());
                return;
            }
            if (outcome.kind() != InitialClaimBlockGrant.GrantOutcome.Kind.APPLIED) return;

            long balance = outcome.balance().balance();
            long granted = service.startingBalance();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Component.text(
                        "Welcome! You received " + granted
                                + " starter claim blocks. Balance: " + balance,
                        NamedTextColor.GOLD));
            });
        });
    }
}
