package dev.ankiesmp.dominium.paper;

import dev.ankiesmp.dominium.core.access.ClaimSettingsLookup;
import dev.ankiesmp.dominium.core.ledger.AdminGrantAction;
import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
import dev.ankiesmp.dominium.core.territory.ClaimBorderSelector;
import dev.ankiesmp.dominium.paper.command.ClaimCommand;
import dev.ankiesmp.dominium.paper.command.ConfirmationStore;
import dev.ankiesmp.dominium.paper.command.DominiumCommand;
import dev.ankiesmp.dominium.paper.command.KingdomCommand;
import dev.ankiesmp.dominium.paper.config.DominiumConfig;
import dev.ankiesmp.dominium.paper.gui.KingdomGuiListener;
import dev.ankiesmp.dominium.paper.gui.KingdomGuiService;
import dev.ankiesmp.dominium.paper.hud.TerritoryHudTask;
import dev.ankiesmp.dominium.paper.listener.ActivityListener;
import dev.ankiesmp.dominium.paper.listener.InitialGrantListener;
import dev.ankiesmp.dominium.paper.listener.TerritoryHudListener;
import dev.ankiesmp.dominium.paper.particles.BorderParticleClearListener;
import dev.ankiesmp.dominium.paper.particles.ClaimBorderParticleTask;
import dev.ankiesmp.dominium.paper.player.BukkitPlayerLookup;
import dev.ankiesmp.dominium.paper.protection.BuildListener;
import dev.ankiesmp.dominium.paper.protection.ContainerListener;
import dev.ankiesmp.dominium.paper.protection.EntityListener;
import dev.ankiesmp.dominium.paper.protection.EnvironmentListener;
import dev.ankiesmp.dominium.paper.protection.InteractListener;
import dev.ankiesmp.dominium.paper.protection.PvPListener;
import dev.ankiesmp.dominium.paper.scheduler.ActivityFlushTask;
import dev.ankiesmp.dominium.paper.scheduler.EarningTask;
import dev.ankiesmp.dominium.paper.scheduler.InactivityScanTask;
import dev.ankiesmp.dominium.paper.service.DominiumServices;
import dev.ankiesmp.dominium.paper.tool.ClaimToolListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DominiumPlugin extends JavaPlugin {

    private DominiumServices services;
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private ConfirmationStore confirmations;
    private dev.ankiesmp.dominium.paper.command.ClaimDeleteConfirmations deleteConfirmations;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        DominiumConfig config = DominiumConfig.fromPlugin(this);
        try {
            services = DominiumServices.bootstrap(this, config, getDataFolder().toPath());
        } catch (RuntimeException e) {
            getSLF4JLogger().error("Failed to bootstrap Dominium services; disabling plugin", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        confirmations = new ConfirmationStore(config.kingdom().confirmTtlSeconds());
        deleteConfirmations = new dev.ankiesmp.dominium.paper.command.ClaimDeleteConfirmations(30L);

        registerListeners();
        registerCommands(config);
        scheduleTasks(config);

        if (services.claimRepairMode()) {
            getSLF4JLogger().warn(
                    "======================================================================");
            getSLF4JLogger().warn("Dominium started in CLAIM_REPAIR_MODE.");
            getSLF4JLogger().warn(
                    "Duplicate claims were detected in this database. V6 single-claim");
            getSLF4JLogger().warn(
                    "unique index has NOT been installed. Affected owners cannot create");
            getSLF4JLogger().warn(
                    "or resize claims. Resolve via /dominium claims inspect + delete,");
            getSLF4JLogger().warn(
                    "then restart the server. See earlier warnings for the conflict list.");
            getSLF4JLogger().warn(
                    "======================================================================");
        }
        getSLF4JLogger().info("Dominium enabled - services online{}.",
                services.claimRepairMode() ? " (CLAIM_REPAIR_MODE)" : "");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new ClaimToolListener(
                services.claimTool(), services.selectionState(),
                services.claimIndex(), services.ledger()), this);
        pm.registerEvents(new BuildListener(services.protectionGuard()), this);
        pm.registerEvents(new InteractListener(services.protectionGuard()), this);
        pm.registerEvents(new ContainerListener(services.protectionGuard()), this);
        pm.registerEvents(new PvPListener(services.protectionGuard()), this);
        pm.registerEvents(new EnvironmentListener(services.protectionGuard(), services.claimIndex()), this);
        pm.registerEvents(new EntityListener(services.protectionGuard()), this);
        pm.registerEvents(new InitialGrantListener(
                this, services.initialClaimBlockGrant(), services.dbExecutor()), this);
        pm.registerEvents(new ActivityListener(services.activityTracker()), this);
        pm.registerEvents(new TerritoryHudListener(services.territoryCache()), this);

        // Wist confirm-state bij quit (fase-4 invariant).
        pm.registerEvents(new Listener() {
            @EventHandler public void onQuit(PlayerQuitEvent event) {
                if (confirmations != null) confirmations.clear(event.getPlayer().getUniqueId());
                services.kingdomCache().invalidatePlayer(event.getPlayer().getUniqueId());
            }
        }, this);
    }

    private void registerCommands(DominiumConfig config) {
        PlayerTargetResolver targetResolver = new PlayerTargetResolver(
                new BukkitPlayerLookup(getServer()));

        var claimCmd = new ClaimCommand(
                services.claimTool(),
                services.selectionState(),
                services.claimIndex(),
                services.claimService(),
                services.ledger(),
                services.accessService(),
                targetResolver,
                services.earner(),
                services.territoryCache(),
                services.kingdomService(),
                services.worldGuardHook(),
                config.worldGuard(),
                services.dbExecutor());
        var claim = Objects.requireNonNull(getCommand("claim"));
        claim.setExecutor(claimCmd);
        claim.setTabCompleter(claimCmd);

        AdminGrantAction adminGrantAction = new AdminGrantAction(
                targetResolver, services.claimBlockAdminOps());
        var borderSelector = new dev.ankiesmp.dominium.core.territory.ClaimBorderSelector(
                services.claimIndex(), services.kingdomCache());
        var dominiumCmd = new DominiumCommand(
                services.claimTool(),
                adminGrantAction,
                targetResolver,
                services.kingdomClaimBlockAdminOps(),
                services.kingdomService(),
                services.claimService(),
                services.claimIndex(),
                services.territoryCache(),
                deleteConfirmations,
                services.claimMutationGuard(),
                services.ledger(),
                borderSelector,
                config.borderParticles(),
                services.bankStore(),
                services.atomicClaimOps(),
                services.worldGuardHook(),
                config.worldGuard(),
                services.dbExecutor(),
                getPluginMeta().getVersion());
        var dominium = Objects.requireNonNull(getCommand("dominium"));
        dominium.setExecutor(dominiumCmd);
        dominium.setTabCompleter(dominiumCmd);

        var guiService = new KingdomGuiService(this,
                services.kingdomService(),
                services.kingdomMembershipService(),
                services.kingdomInviteService(),
                services.kingdomVisitorService(),
                services.kingdomBankService(),
                services.kingdomClaimBlockPool(),
                services.bankStore(),
                services.ledger(),
                services.claimIndex(),
                services.dbExecutor());
        var kingdomCmd = new KingdomCommand(
                services.kingdomService(),
                services.kingdomMembershipService(),
                services.kingdomInviteService(),
                services.kingdomVisitorService(),
                targetResolver,
                confirmations,
                services.dbExecutor(),
                guiService,
                services.kingdomBankService(),
                services.kingdomClaimBlockPool());
        var kingdom = Objects.requireNonNull(getCommand("kingdom"));
        kingdom.setExecutor(kingdomCmd);
        kingdom.setTabCompleter(kingdomCmd);

        getServer().getPluginManager().registerEvents(
                new KingdomGuiListener(guiService), this);
    }

    private void scheduleTasks(DominiumConfig config) {
        long flushTicks = Math.max(20L, config.activity().flushIntervalSeconds() * 20L);
        var flush = new ActivityFlushTask(services.activityTracker(),
                services.activityStore(), services.dbExecutor(), getSLF4JLogger());
        scheduledTasks.add(getServer().getScheduler().runTaskTimerAsynchronously(
                this, flush, flushTicks, flushTicks));

        if (services.earner().enabled()) {
            long earnTicks = Math.max(20L, config.earning().tickSeconds() * 20L);
            var earnTask = new EarningTask(services.earner(),
                    services.activityTracker(), services.dbExecutor(), getSLF4JLogger(),
                    getServer());
            scheduledTasks.add(getServer().getScheduler().runTaskTimerAsynchronously(
                    this, earnTask, earnTicks, earnTicks));
        }

        services.inactivityExpiry().ifPresent(expiry -> {
            long scanTicks = Math.max(1200L, config.expiry().scanIntervalMinutes() * 60L * 20L);
            var scan = new InactivityScanTask(expiry, services.dbExecutor(),
                    getSLF4JLogger(),
                    id -> getServer().getPlayer(id) != null);
            scheduledTasks.add(getServer().getScheduler().runTaskTimerAsynchronously(
                    this, scan, scanTicks, scanTicks));
        });

        // Invite cleanup: elke 60s.
        scheduledTasks.add(getServer().getScheduler().runTaskTimerAsynchronously(
                this, () -> services.dbExecutor().execute(() -> {
                    try { services.kingdomInviteService().cleanupExpired(); }
                    catch (RuntimeException ex) {
                        getSLF4JLogger().warn("Invite cleanup failed: {}", ex.toString());
                    }
                }), 20L * 60L, 20L * 60L));

        // Border particles: synchroon (spawnParticle vereist main thread).
        if (config.borderParticles().enabled()) {
            var selector = new ClaimBorderSelector(services.claimIndex(), services.kingdomCache());
            var task = new ClaimBorderParticleTask(getServer(), selector,
                    config.borderParticles(), getSLF4JLogger());
            long ticks = Math.max(1L, config.borderParticles().updateIntervalTicks());
            scheduledTasks.add(getServer().getScheduler().runTaskTimer(this, task, ticks, ticks));
            getServer().getPluginManager().registerEvents(
                    new BorderParticleClearListener(task), this);
        }

        // Constante actionbar-HUD.
        if (config.territoryHud().enabled()) {
            var hudTask = new TerritoryHudTask(getServer(), services.territoryCache(),
                    services.kingdomService(), config.territoryHud(), getSLF4JLogger());
            long ticks = Math.max(1L, config.territoryHud().refreshIntervalTicks());
            scheduledTasks.add(getServer().getScheduler().runTaskTimer(this, hudTask, ticks, ticks));
        }
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : scheduledTasks) {
            try { t.cancel(); } catch (Throwable ignored) {}
        }
        scheduledTasks.clear();
        if (services != null) {
            try {
                var deltas = services.activityTracker().drainAll();
                if (!deltas.isEmpty()) {
                    services.activityStore().flushBatch(deltas, System.currentTimeMillis());
                }
            } catch (RuntimeException ex) {
                getSLF4JLogger().warn("Final activity flush failed: {}", ex.toString());
            }
            try { services.kingdomCache().clear(); } catch (RuntimeException ignored) {}
            services.close();
            services = null;
        }
        getSLF4JLogger().info("Dominium disabled.");
    }

    public DominiumServices services() { return services; }

    @SuppressWarnings("unused")
    private static ClaimSettingsLookup dummy() { return ClaimSettingsLookup.NEVER; }
}
