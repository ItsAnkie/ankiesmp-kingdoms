package dev.ankiesmp.dominium.paper.service;

import dev.ankiesmp.dominium.api.ClaimBlockService;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessService;
import dev.ankiesmp.dominium.core.activity.ActivityTracker;
import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import dev.ankiesmp.dominium.core.bootstrap.DominiumCore;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.earning.ActivePlayEarner;
import dev.ankiesmp.dominium.core.expiry.InactivityExpiryService;
import dev.ankiesmp.dominium.core.kingdom.KingdomCache;
import dev.ankiesmp.dominium.core.kingdom.KingdomInviteService;
import dev.ankiesmp.dominium.core.kingdom.KingdomMembershipService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitorService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.InitialClaimBlockGrant;
import dev.ankiesmp.dominium.core.ledger.KingdomClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.protection.ProtectionService;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import dev.ankiesmp.dominium.paper.config.DominiumConfig;
import dev.ankiesmp.dominium.paper.protection.ProtectionGuard;
import dev.ankiesmp.dominium.paper.tool.ClaimTool;
import dev.ankiesmp.dominium.paper.tool.SelectionState;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Bukkit-aware wrapper rond {@link DominiumCore}.
 */
public final class DominiumServices implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DominiumServices.class);

    private final DominiumCore core;
    private final ClaimTool claimTool;
    private final SelectionState selectionState;
    private final ProtectionGuard protectionGuard;

    private DominiumServices(DominiumCore core, ClaimTool claimTool,
                             SelectionState selectionState, ProtectionGuard protectionGuard) {
        this.core = Objects.requireNonNull(core);
        this.claimTool = claimTool;
        this.selectionState = selectionState;
        this.protectionGuard = Objects.requireNonNull(protectionGuard);
    }

    public static DominiumServices bootstrap(Plugin plugin, DominiumConfig config, Path dataFolder) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        var vault = dev.ankiesmp.dominium.paper.bank.BukkitVaultAdapter.detect(LOG);
        var wg = config.worldGuard().enabled()
                ? dev.ankiesmp.dominium.paper.integrations.BukkitWorldGuardHook.detect(LOG)
                : dev.ankiesmp.dominium.core.integrations.WorldGuardHook.NO_OP;
        DominiumCore core = DominiumCore.bootstrap(new DominiumCore.BootstrapConfig(
                dataFolder, config.sqliteFile(),
                config.startingClaimBlocks(),
                config.activity().windowSeconds(),
                config.earning().blocksPerInterval(),
                config.earning().intervalSeconds(),
                config.earning().dailyCap(),
                config.expiry().enabled(),
                config.expiry().inactivityDays(),
                config.expiry().minAgeDays(),
                config.kingdom().nameMinLength(),
                config.kingdom().nameMaxLength(),
                config.kingdom().inviteTtlMinutes(),
                config.kingdomBank().maxBalanceMinor(),
                config.kingdomClaimBlocks().contributionEnabled(),
                config.kingdomClaimBlocks().purchasingEnabled(),
                config.kingdomClaimBlocks().pricePerBlockMinor(),
                config.kingdomClaimBlocks().maxPurchasePerOperation(),
                vault, wg,
                Clock.systemUTC()));
        try {
            ClaimTool claimTool = new ClaimTool(plugin);
            SelectionState selectionState = new SelectionState();
            ProtectionGuard guard = new ProtectionGuard(core.protectionService());
            LOG.info("Dominium services online.");
            return new DominiumServices(core, claimTool, selectionState, guard);
        } catch (RuntimeException | Error fatal) {
            LOG.error("Dominium service wrapper failed after core startup - closing core", fatal);
            core.close();
            throw fatal;
        }
    }

    public dev.ankiesmp.dominium.storage.db.Database database() { return core.database(); }
    public ClaimBlockLedger ledger() { return core.ledger(); }
    public ClaimBlockService claimBlockService() { return core.claimBlockService(); }
    public ClaimIndex claimIndex() { return core.claimIndex(); }
    public ClaimService claimService() { return core.claimService(); }
    public ClaimTool claimTool() { return claimTool; }
    public SelectionState selectionState() { return selectionState; }
    public ProtectionService protectionService() { return core.protectionService(); }
    public ProtectionGuard protectionGuard() { return protectionGuard; }
    public InitialClaimBlockGrant initialClaimBlockGrant() { return core.initialClaimBlockGrant(); }
    public ClaimBlockAdminOps claimBlockAdminOps() { return core.claimBlockAdminOps(); }
    public PersonalClaimAccessService accessService() { return core.accessService(); }
    public TerritoryContextCache territoryCache() { return core.territoryCache(); }
    public PlayerActivityStore activityStore() { return core.activityStore(); }
    public ActivityTracker activityTracker() { return core.activityTracker(); }
    public ActivePlayEarner earner() { return core.earner(); }
    public Optional<InactivityExpiryService> inactivityExpiry() { return core.inactivityExpiry(); }
    public KingdomService kingdomService() { return core.kingdomService(); }
    public KingdomMembershipService kingdomMembershipService() { return core.kingdomMembershipService(); }
    public KingdomInviteService kingdomInviteService() { return core.kingdomInviteService(); }
    public KingdomVisitorService kingdomVisitorService() { return core.kingdomVisitorService(); }
    public KingdomClaimBlockAdminOps kingdomClaimBlockAdminOps() { return core.kingdomClaimBlockAdminOps(); }
    public boolean claimRepairMode() { return core.claimRepairMode(); }
    public dev.ankiesmp.dominium.core.claim.MutableClaimMutationGuard claimMutationGuard() {
        return core.claimMutationGuard();
    }
    public KingdomCache kingdomCache() { return core.kingdomCache(); }
    public dev.ankiesmp.dominium.core.bank.KingdomBankService kingdomBankService() {
        return core.kingdomBankService();
    }
    public dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService kingdomClaimBlockPool() {
        return core.kingdomClaimBlockPool();
    }
    public dev.ankiesmp.dominium.core.bank.BankStore bankStore() { return core.bankStore(); }
    public dev.ankiesmp.dominium.core.integrations.WorldGuardHook worldGuardHook() {
        return core.worldGuardHook();
    }
    public dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps() {
        return core.atomicClaimOps();
    }
    public Executor dbExecutor() { return core.dbExecutor(); }
    public DominiumCore core() { return core; }

    @Override
    public void close() {
        core.close();
    }
}
