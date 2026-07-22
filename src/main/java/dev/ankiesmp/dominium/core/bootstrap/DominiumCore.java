package dev.ankiesmp.dominium.core.bootstrap;

import dev.ankiesmp.dominium.api.ClaimBlockService;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessService;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessStore;
import dev.ankiesmp.dominium.core.activity.ActivityTracker;
import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.PlacementRules;
import dev.ankiesmp.dominium.core.claim.PlacementValidator;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.earning.ActivePlayEarner;
import dev.ankiesmp.dominium.core.earning.EarningStore;
import dev.ankiesmp.dominium.core.expiry.InactivityExpiryService;
import dev.ankiesmp.dominium.core.kingdom.KingdomCache;
import dev.ankiesmp.dominium.core.kingdom.KingdomInviteService;
import dev.ankiesmp.dominium.core.kingdom.KingdomMembershipService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.kingdom.KingdomStore;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitorService;
import dev.ankiesmp.dominium.core.bank.BankStore;
import dev.ankiesmp.dominium.core.bank.KingdomBankService;
import dev.ankiesmp.dominium.core.bank.KingdomClaimBlockPoolService;
import dev.ankiesmp.dominium.core.bank.VaultAdapter;
import dev.ankiesmp.dominium.core.claim.ClaimMutationGuard;
import dev.ankiesmp.dominium.core.claim.DuplicateOwnerAudit;
import dev.ankiesmp.dominium.core.claim.MutableClaimMutationGuard;
import dev.ankiesmp.dominium.core.integrations.WorldGuardHook;
import dev.ankiesmp.dominium.storage.bank.SqlBankStore;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.InitialClaimBlockGrant;
import dev.ankiesmp.dominium.core.ledger.KingdomClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.protection.AudienceResolver;
import dev.ankiesmp.dominium.core.protection.FlagDefaults;
import dev.ankiesmp.dominium.core.protection.KingdomAwareAudienceResolver;
import dev.ankiesmp.dominium.core.protection.PersonalClaimAudienceResolver;
import dev.ankiesmp.dominium.core.protection.ProtectionService;
import dev.ankiesmp.dominium.core.territory.TerritoryContextCache;
import dev.ankiesmp.dominium.storage.access.SqlPersonalClaimAccessStore;
import dev.ankiesmp.dominium.storage.activity.SqlPlayerActivityStore;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.claim.SqlClaimRepository;
import dev.ankiesmp.dominium.storage.claim.SqlClaimStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.earning.SqlEarningStore;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import dev.ankiesmp.dominium.storage.migrations.SingleClaimIndexInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pure-core service graph. Bevat <b>geen</b> Bukkit-imports zodat tests
 * de volledige startup kunnen doorlopen zonder Paper API op de
 * test-classpath te hoeven zetten.
 *
 * <p>Sinds fase 3 leven ook access/cache/activity/earning/expiry hier.
 * De Bukkit-plugin wrapt dit in {@code DominiumServices} en voegt daar
 * shovel-tool, listeners, commands en scheduled tasks aan toe.
 *
 * <p>{@link #bootstrap} is exception-safe: partieel opgezette resources
 * worden bij een fout gesloten voordat de exception naar buiten komt.
 */
public final class DominiumCore implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DominiumCore.class);

    private final Database database;
    private final ClaimBlockLedger ledger;
    private final ClaimBlockService claimBlockService;
    private final ClaimIndex claimIndex;
    private final ClaimService claimService;
    private final ProtectionService protectionService;
    private final InitialClaimBlockGrant initialClaimBlockGrant;
    private final ClaimBlockAdminOps claimBlockAdminOps;
    private final PersonalClaimAccessStore accessStore;
    private final PersonalClaimAccessService accessService;
    private final TerritoryContextCache territoryCache;
    private final PlayerActivityStore activityStore;
    private final ActivityTracker activityTracker;
    private final EarningStore earningStore;
    private final ActivePlayEarner earner;
    private final InactivityExpiryService inactivityExpiry;
    private final KingdomStore kingdomStore;
    private final KingdomCache kingdomCache;
    private final KingdomService kingdomService;
    private final KingdomMembershipService kingdomMembershipService;
    private final KingdomInviteService kingdomInviteService;
    private final KingdomVisitorService kingdomVisitorService;
    private final KingdomClaimBlockAdminOps kingdomClaimBlockAdminOps;
    private final boolean claimRepairMode;
    private final java.util.List<SingleClaimIndexInstaller.OwnerConflict> claimConflicts;
    private final MutableClaimMutationGuard claimMutationGuard;
    private final BankStore bankStore;
    private final KingdomBankService kingdomBankService;
    private final KingdomClaimBlockPoolService kingdomClaimBlockPool;
    private final WorldGuardHook worldGuardHook;
    private final dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps;
    private final ExecutorService dbExecutor;

    private DominiumCore(Database database, ClaimBlockLedger ledger,
                         ClaimBlockService claimBlockService,
                         ClaimIndex claimIndex, ClaimService claimService,
                         ProtectionService protectionService,
                         InitialClaimBlockGrant initialClaimBlockGrant,
                         ClaimBlockAdminOps claimBlockAdminOps,
                         PersonalClaimAccessStore accessStore,
                         PersonalClaimAccessService accessService,
                         TerritoryContextCache territoryCache,
                         PlayerActivityStore activityStore,
                         ActivityTracker activityTracker,
                         EarningStore earningStore, ActivePlayEarner earner,
                         InactivityExpiryService inactivityExpiry,
                         KingdomStore kingdomStore, KingdomCache kingdomCache,
                         KingdomService kingdomService,
                         KingdomMembershipService kingdomMembershipService,
                         KingdomInviteService kingdomInviteService,
                         KingdomVisitorService kingdomVisitorService,
                         KingdomClaimBlockAdminOps kingdomClaimBlockAdminOps,
                         boolean claimRepairMode,
                         java.util.List<SingleClaimIndexInstaller.OwnerConflict> claimConflicts,
                         MutableClaimMutationGuard claimMutationGuard,
                         BankStore bankStore, KingdomBankService kingdomBankService,
                         KingdomClaimBlockPoolService kingdomClaimBlockPool,
                         WorldGuardHook worldGuardHook,
                         dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps,
                         ExecutorService dbExecutor) {
        this.database = database;
        this.ledger = ledger;
        this.claimBlockService = claimBlockService;
        this.claimIndex = claimIndex;
        this.claimService = claimService;
        this.protectionService = protectionService;
        this.initialClaimBlockGrant = initialClaimBlockGrant;
        this.claimBlockAdminOps = claimBlockAdminOps;
        this.accessStore = accessStore;
        this.accessService = accessService;
        this.territoryCache = territoryCache;
        this.activityStore = activityStore;
        this.activityTracker = activityTracker;
        this.earningStore = earningStore;
        this.earner = earner;
        this.inactivityExpiry = inactivityExpiry;
        this.kingdomStore = kingdomStore;
        this.kingdomCache = kingdomCache;
        this.kingdomService = kingdomService;
        this.kingdomMembershipService = kingdomMembershipService;
        this.kingdomInviteService = kingdomInviteService;
        this.kingdomVisitorService = kingdomVisitorService;
        this.kingdomClaimBlockAdminOps = kingdomClaimBlockAdminOps;
        this.claimRepairMode = claimRepairMode;
        this.claimConflicts = java.util.List.copyOf(claimConflicts);
        this.claimMutationGuard = claimMutationGuard;
        this.bankStore = bankStore;
        this.kingdomBankService = kingdomBankService;
        this.kingdomClaimBlockPool = kingdomClaimBlockPool;
        this.worldGuardHook = worldGuardHook;
        this.atomicClaimOps = atomicClaimOps;
        this.dbExecutor = dbExecutor;
    }

    /** Backwards-compatible overload voor tests: startsaldo 0, geen earning, geen expiry. */
    public static DominiumCore bootstrap(Path dataFolder, String sqliteFileName,
                                         AudienceResolver ignored) {
        return bootstrap(BootstrapConfig.defaults(dataFolder, sqliteFileName, 0L));
    }

    /** Backwards-compatible overload met alleen starting balance instelbaar. */
    public static DominiumCore bootstrap(Path dataFolder, String sqliteFileName,
                                         AudienceResolver ignored, long startingClaimBlocks) {
        return bootstrap(BootstrapConfig.defaults(dataFolder, sqliteFileName, startingClaimBlocks));
    }

    public static DominiumCore bootstrap(BootstrapConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        try {
            Files.createDirectories(cfg.dataFolder());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create data folder " + cfg.dataFolder(), e);
        }
        Path dbFile = cfg.dataFolder().resolve(cfg.sqliteFileName());
        String jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();

        Database db = null;
        ExecutorService executor = null;
        try {
            logDriverProvenance();
            db = Database.sqlite(jdbcUrl);
            logDriverVersion(db);

            new MigrationRunner(db).migrate();

            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "dominium-db");
                t.setDaemon(true);
                return t;
            });

            SqlClaimBlockLedger sqlLedger = new SqlClaimBlockLedger(db);
            ClaimBlockLedger ledger = sqlLedger;
            ClaimBlockService service = new AsyncClaimBlockService(ledger, executor);

            SqlClaimRepository claimRepo = new SqlClaimRepository(db);
            ClaimIndex claimIndex = new ClaimIndex();
            for (var claim : claimRepo.loadAll()) claimIndex.add(claim);
            PlacementValidator validator = new PlacementValidator(claimIndex, PlacementRules.defaults());

            // Preflight voor de "één claim per owner"-unique index. Bij duplicaten
            // wordt V6 NIET geplaatst; guard blokkeert alleen die specifieke owners.
            SingleClaimIndexInstaller.Result installer = SingleClaimIndexInstaller.install(db);
            boolean repairMode = installer.deferred();
            MutableClaimMutationGuard guard = new MutableClaimMutationGuard();
            if (repairMode) {
                LOG.warn("Claim repair mode: {} owner(s) have multiple claims. "
                        + "V6 single-claim unique index is DEFERRED until resolved.",
                        installer.conflicts().size());
                for (var c : installer.conflicts()) {
                    guard.block(c.type(), c.ownerId());
                    for (var cd : c.claims()) {
                        LOG.warn("  conflict owner={}/{}  claim-id={}  world={}  rect=({},{}..{},{})",
                                c.type(), c.ownerId(), cd.id(), cd.worldId(),
                                cd.minX(), cd.minZ(), cd.maxX(), cd.maxZ());
                    }
                }
                LOG.warn("  Resolve with /dominium claims repair <owner>, then restart.");
            } else if (installer.kind() == SingleClaimIndexInstaller.Kind.APPLIED) {
                LOG.info("V6 single-claim unique index installed.");
            }
            WorldGuardHook wgForService = cfg.worldGuardHook();
            ClaimService claimService = new ClaimService(
                    claimIndex, validator, ledger,
                    new SqlClaimStore(claimRepo, db, sqlLedger), guard,
                    wgForService, /* ignoreGlobalRegion */ true);
            dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps =
                    new dev.ankiesmp.dominium.storage.claim.AtomicClaimOps(db, claimRepo, sqlLedger);

            PersonalClaimAccessStore accessStore = new SqlPersonalClaimAccessStore(db);
            TerritoryContextCache cache = new TerritoryContextCache(
                    claimIndex,
                    accessStore::levelFor,
                    id -> accessStore.settingsFor(id).noAccess());

            KingdomStore kingdomStore = new SqlKingdomStore(db);
            KingdomCache kingdomCache = new KingdomCache(kingdomStore);

            AudienceResolver personalResolver = new PersonalClaimAudienceResolver(cache);
            AudienceResolver resolver = new KingdomAwareAudienceResolver(personalResolver, kingdomCache);
            ProtectionService protection = new ProtectionService(
                    claimIndex, resolver, FlagDefaults.standard(), cache);

            var invalidator = new PersonalClaimAccessService.Invalidator() {
                @Override public void onAccessChanged(java.util.UUID claimId, java.util.UUID playerId) {
                    cache.invalidateAccess(claimId, playerId);
                }
                @Override public void onSettingsChanged(java.util.UUID claimId) {
                    cache.invalidateSettings(claimId);
                }
            };
            PersonalClaimAccessService accessService = new PersonalClaimAccessService(
                    accessStore, cfg.clock(), invalidator);

            InitialClaimBlockGrant initialGrant = new InitialClaimBlockGrant(
                    ledger, cfg.startingClaimBlocks());
            ClaimBlockAdminOps adminOps = new ClaimBlockAdminOps(ledger);

            PlayerActivityStore activityStore = new SqlPlayerActivityStore(db);
            ActivityTracker tracker = new ActivityTracker(
                    Math.max(1L, cfg.activityWindowSeconds()),
                    System::nanoTime, cfg.clock()::millis);

            EarningStore earningStore = new SqlEarningStore(db);
            ActivePlayEarner earner = new ActivePlayEarner(ledger, earningStore,
                    new ActivePlayEarner.EarningConfig(
                            cfg.earningBlocksPerInterval(),
                            Math.max(1L, cfg.earningIntervalSeconds()),
                            cfg.earningDailyCap()));

            long dayMs = 86_400_000L;
            var expiryCfg = cfg.expiryEnabled()
                    ? new InactivityExpiryService.Config(true,
                            cfg.expiryInactivityDays() * dayMs,
                            cfg.expiryMinAgeDays() * dayMs)
                    : InactivityExpiryService.Config.disabled();
            InactivityExpiryService expiry = new InactivityExpiryService(
                    claimIndex, claimService, activityStore, cfg.clock(),
                    id -> false, expiryCfg,
                    (java.util.function.Consumer<java.util.UUID>) cache::invalidateClaim);

            var kingdomInvalidator = new KingdomService.Invalidator() {
                @Override public void onMembershipChanged(java.util.UUID kingdomId, java.util.UUID playerUuid) {
                    kingdomCache.invalidatePlayer(playerUuid);
                }
                @Override public void onKingdomDisbanded(java.util.UUID kingdomId) {
                    kingdomCache.invalidateKingdom(kingdomId);
                }
            };
            var kingdomService = new KingdomService(kingdomStore, cfg.clock(),
                    new KingdomService.NameConfig(cfg.kingdomNameMinLength(), cfg.kingdomNameMaxLength()),
                    kingdomInvalidator);
            var kingdomMembershipService = new KingdomMembershipService(kingdomStore, cfg.clock(), kingdomInvalidator);
            var kingdomInviteService = new KingdomInviteService(kingdomStore, cfg.clock(),
                    java.time.Duration.ofMinutes(Math.max(1, cfg.inviteTtlMinutes())),
                    kingdomInvalidator);
            var kingdomVisitorService = new KingdomVisitorService(kingdomStore, cfg.clock(), kingdomInvalidator);

            KingdomClaimBlockAdminOps kingdomClaimBlockAdminOps = new KingdomClaimBlockAdminOps(ledger);

            BankStore bankStore = new SqlBankStore(db);
            KingdomBankService bankService = new KingdomBankService(bankStore, kingdomService,
                    cfg.vaultAdapter(), cfg.clock(), cfg.bankMaxBalanceMinor());
            int recovered = bankService.recoverIncompleteOperations();
            if (recovered > 0) {
                LOG.warn("Bank recovery: {} incomplete operation(s) marked COMPENSATION_REQUIRED. "
                        + "Review via /dominium bank operations.", recovered);
            }
            KingdomClaimBlockPoolService kingdomClaimBlockPool = new KingdomClaimBlockPoolService(
                    ledger, bankService, kingdomService,
                    new KingdomClaimBlockPoolService.Config(
                            cfg.contributionEnabled(), cfg.purchasingEnabled(),
                            cfg.pricePerBlockMinor(), cfg.maxPurchasePerOperation()));
            WorldGuardHook worldGuardHook = wgForService;

            // Ter dubbele zekerheid: in-memory audit (moet leeg zijn wanneer V6 net is geplaatst).
            for (var conflict : DuplicateOwnerAudit.scan(claimIndex)) {
                LOG.warn("Duplicate {}-owner claim detected for {}: claim-ids={} "
                                + "(use /dominium claims inspect + delete/transfer to resolve).",
                        conflict.type(), conflict.ownerId(), conflict.claimIds());
            }

            LOG.info("Initial claim block grant {}",
                    initialGrant.enabled()
                            ? "enabled: " + initialGrant.startingBalance() + " blocks per new player."
                            : "disabled (starting-balance = 0).");
            LOG.info("Active-play earner {}",
                    earner.enabled()
                            ? "enabled: " + cfg.earningBlocksPerInterval() + " blocks per "
                                    + cfg.earningIntervalSeconds() + "s, daily cap "
                                    + cfg.earningDailyCap() + "."
                            : "disabled.");
            LOG.info("Inactivity expiry {}",
                    expiryCfg.enabled()
                            ? "enabled: threshold " + cfg.expiryInactivityDays() + "d, min-age "
                                    + cfg.expiryMinAgeDays() + "d."
                            : "disabled.");
            LOG.info("Dominium core online.");

            return new DominiumCore(db, ledger, service, claimIndex, claimService, protection,
                    initialGrant, adminOps, accessStore, accessService, cache,
                    activityStore, tracker, earningStore, earner, expiry,
                    kingdomStore, kingdomCache, kingdomService, kingdomMembershipService,
                    kingdomInviteService, kingdomVisitorService, kingdomClaimBlockAdminOps,
                    repairMode, installer.conflicts(), guard,
                    bankStore, bankService, kingdomClaimBlockPool, worldGuardHook,
                    atomicClaimOps, executor);
        } catch (RuntimeException | Error fatal) {
            LOG.error("Dominium core bootstrap failed - releasing partial resources", fatal);
            shutdownExecutorSilently(executor);
            closeDatabaseSilently(db);
            throw fatal;
        }
    }

    private static void logDriverProvenance() {
        for (String className : new String[]{ "org.sqlite.JDBC", "org.sqlite.core.NativeDB" }) {
            try {
                Class<?> clazz = Class.forName(className);
                ClassLoader cl = clazz.getClassLoader();
                var domain = clazz.getProtectionDomain();
                var src = domain == null ? null : domain.getCodeSource();
                LOG.info("Dominium driver provenance: {} loaded from {} via classloader {}",
                        className,
                        src == null || src.getLocation() == null ? "<unknown>" : src.getLocation(),
                        cl == null ? "<bootstrap>" : cl);
            } catch (ClassNotFoundException e) {
                LOG.warn("Dominium driver provenance: {} not on classpath: {}", className, e.getMessage());
            } catch (LinkageError e) {
                LOG.warn("Dominium driver provenance: {} link error: {}", className, e.getMessage());
            }
        }
    }

    private static void logDriverVersion(Database db) {
        try {
            db.execute(conn -> {
                DatabaseMetaData md = conn.getMetaData();
                LOG.info("Dominium JDBC driver: {} {}",
                        md.getDriverName(), md.getDriverVersion());
            });
        } catch (RuntimeException e) {
            LOG.warn("Could not inspect JDBC driver metadata: {}", e.getMessage());
        }
    }

    static void shutdownExecutorSilently(ExecutorService executor) {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    static void closeDatabaseSilently(Database db) {
        if (db == null) return;
        try {
            db.close();
        } catch (RuntimeException ex) {
            LOG.warn("Failed to close database during cleanup: {}", ex.getMessage());
        }
    }

    public Database database() { return database; }
    public ClaimBlockLedger ledger() { return ledger; }
    public ClaimBlockService claimBlockService() { return claimBlockService; }
    public ClaimIndex claimIndex() { return claimIndex; }
    public ClaimService claimService() { return claimService; }
    public ProtectionService protectionService() { return protectionService; }
    public InitialClaimBlockGrant initialClaimBlockGrant() { return initialClaimBlockGrant; }
    public ClaimBlockAdminOps claimBlockAdminOps() { return claimBlockAdminOps; }
    public PersonalClaimAccessStore accessStore() { return accessStore; }
    public PersonalClaimAccessService accessService() { return accessService; }
    public TerritoryContextCache territoryCache() { return territoryCache; }
    public PlayerActivityStore activityStore() { return activityStore; }
    public ActivityTracker activityTracker() { return activityTracker; }
    public EarningStore earningStore() { return earningStore; }
    public ActivePlayEarner earner() { return earner; }
    public Optional<InactivityExpiryService> inactivityExpiry() {
        return inactivityExpiry.enabled() ? Optional.of(inactivityExpiry) : Optional.empty();
    }
    public InactivityExpiryService inactivityExpiryRaw() { return inactivityExpiry; }
    public KingdomStore kingdomStore() { return kingdomStore; }
    public KingdomCache kingdomCache() { return kingdomCache; }
    public KingdomService kingdomService() { return kingdomService; }
    public KingdomMembershipService kingdomMembershipService() { return kingdomMembershipService; }
    public KingdomInviteService kingdomInviteService() { return kingdomInviteService; }
    public KingdomVisitorService kingdomVisitorService() { return kingdomVisitorService; }
    public KingdomClaimBlockAdminOps kingdomClaimBlockAdminOps() { return kingdomClaimBlockAdminOps; }
    public boolean claimRepairMode() { return claimRepairMode; }
    public java.util.List<SingleClaimIndexInstaller.OwnerConflict> claimConflicts() { return claimConflicts; }
    public MutableClaimMutationGuard claimMutationGuard() { return claimMutationGuard; }
    public BankStore bankStore() { return bankStore; }
    public KingdomBankService kingdomBankService() { return kingdomBankService; }
    public KingdomClaimBlockPoolService kingdomClaimBlockPool() { return kingdomClaimBlockPool; }
    public WorldGuardHook worldGuardHook() { return worldGuardHook; }
    public dev.ankiesmp.dominium.storage.claim.AtomicClaimOps atomicClaimOps() { return atomicClaimOps; }
    public Executor dbExecutor() { return dbExecutor; }
    public ExecutorService dbExecutorService() { return dbExecutor; }

    @Override
    public void close() {
        shutdownExecutorSilently(dbExecutor);
        closeDatabaseSilently(database);
        if (territoryCache != null) territoryCache.clear();
        LOG.info("Dominium core closed (HikariCP pool + db executor released).");
    }

    /** Immutable bootstrap-config zonder Bukkit-types. */
    public record BootstrapConfig(Path dataFolder, String sqliteFileName,
                                  long startingClaimBlocks,
                                  long activityWindowSeconds,
                                  long earningBlocksPerInterval,
                                  long earningIntervalSeconds,
                                  long earningDailyCap,
                                  boolean expiryEnabled,
                                  long expiryInactivityDays,
                                  long expiryMinAgeDays,
                                  int kingdomNameMinLength,
                                  int kingdomNameMaxLength,
                                  long inviteTtlMinutes,
                                  long bankMaxBalanceMinor,
                                  boolean contributionEnabled,
                                  boolean purchasingEnabled,
                                  long pricePerBlockMinor,
                                  long maxPurchasePerOperation,
                                  VaultAdapter vaultAdapter,
                                  WorldGuardHook worldGuardHook,
                                  Clock clock) {
        public BootstrapConfig {
            Objects.requireNonNull(dataFolder);
            Objects.requireNonNull(sqliteFileName);
            Objects.requireNonNull(clock);
            Objects.requireNonNull(vaultAdapter);
            Objects.requireNonNull(worldGuardHook);
            if (startingClaimBlocks < 0) throw new IllegalArgumentException("startingClaimBlocks < 0");
            if (kingdomNameMinLength < 1 || kingdomNameMaxLength < kingdomNameMinLength) {
                throw new IllegalArgumentException("invalid kingdom name length bounds");
            }
            if (inviteTtlMinutes < 1) throw new IllegalArgumentException("inviteTtlMinutes < 1");
            if (bankMaxBalanceMinor <= 0) throw new IllegalArgumentException("bankMaxBalanceMinor <= 0");
            if (pricePerBlockMinor <= 0) throw new IllegalArgumentException("pricePerBlockMinor <= 0");
            if (maxPurchasePerOperation <= 0) throw new IllegalArgumentException("maxPurchasePerOperation <= 0");
        }

        public static BootstrapConfig defaults(Path dataFolder, String sqliteFileName,
                                               long startingClaimBlocks) {
            return new BootstrapConfig(dataFolder, sqliteFileName, startingClaimBlocks,
                    300L, 0L, 300L, 0L, false, 60L, 3L,
                    3, 24, 10L,
                    100_000_000_000L,   // bank max ~1e9 major
                    true, true, 1000L, 10000L,
                    VaultAdapter.NO_OP, WorldGuardHook.NO_OP,
                    Clock.systemUTC());
        }
    }

    /**
     * Async-adapter voor de publieke {@link ClaimBlockService}-API.
     */
    private static final class AsyncClaimBlockService implements ClaimBlockService {
        private final ClaimBlockLedger ledger;
        private final Executor executor;
        AsyncClaimBlockService(ClaimBlockLedger ledger, Executor executor) {
            this.ledger = ledger; this.executor = executor;
        }
        @Override public CompletableFuture<dev.ankiesmp.dominium.api.ClaimBlockResult> grantToPlayer(
                java.util.UUID playerId, long amount,
                dev.ankiesmp.dominium.api.ClaimBlockReason reason,
                String externalRef, java.util.UUID idempotencyKey) {
            return delegate(playerId, amount, reason, externalRef, idempotencyKey, false);
        }
        @Override public CompletableFuture<dev.ankiesmp.dominium.api.ClaimBlockResult> revokeFromPlayer(
                java.util.UUID playerId, long amount,
                dev.ankiesmp.dominium.api.ClaimBlockReason reason,
                String externalRef, java.util.UUID idempotencyKey) {
            return delegate(playerId, amount, reason, externalRef, idempotencyKey, true);
        }
        @Override public CompletableFuture<Long> getPlayerBalance(java.util.UUID playerId) {
            return CompletableFuture.supplyAsync(
                    () -> ledger.balanceOrZero(
                            dev.ankiesmp.dominium.core.common.HolderKey.player(playerId)).balance(),
                    executor);
        }
        @Override public CompletableFuture<Long> getKingdomBalance(java.util.UUID kingdomId) {
            return CompletableFuture.supplyAsync(
                    () -> ledger.balanceOrZero(
                            dev.ankiesmp.dominium.core.common.HolderKey.kingdom(kingdomId)).balance(),
                    executor);
        }
        private CompletableFuture<dev.ankiesmp.dominium.api.ClaimBlockResult> delegate(
                java.util.UUID playerId, long amount,
                dev.ankiesmp.dominium.api.ClaimBlockReason reason,
                String externalRef, java.util.UUID idempotencyKey, boolean negative) {
            if (amount <= 0) {
                return CompletableFuture.completedFuture(
                        dev.ankiesmp.dominium.api.ClaimBlockResult.invalidAmount("amount must be positive"));
            }
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            long delta = negative ? -amount : amount;
            var request = dev.ankiesmp.dominium.core.ledger.PostingRequest.builder()
                    .holder(dev.ankiesmp.dominium.core.common.HolderKey.player(playerId))
                    .delta(delta)
                    .reason(reason)
                    .reference(externalRef)
                    .idempotencyKey(idempotencyKey)
                    .build();
            return CompletableFuture.supplyAsync(() -> {
                var outcome = ledger.post(request);
                return switch (outcome.kind()) {
                    case APPLIED -> dev.ankiesmp.dominium.api.ClaimBlockResult.applied(outcome.balance().balance());
                    case ALREADY_APPLIED ->
                            dev.ankiesmp.dominium.api.ClaimBlockResult.alreadyApplied(outcome.balance().balance());
                    case INSUFFICIENT_BALANCE ->
                            dev.ankiesmp.dominium.api.ClaimBlockResult.insufficientBalance(outcome.balance().balance());
                };
            }, executor);
        }
    }
}
