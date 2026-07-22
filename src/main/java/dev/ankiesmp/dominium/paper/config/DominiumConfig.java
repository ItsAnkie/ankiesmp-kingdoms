package dev.ankiesmp.dominium.paper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Typed view op {@code config.yml}.
 */
public final class DominiumConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DominiumConfig.class);

    private final String databaseType;
    private final String sqliteFile;
    private final long startingClaimBlocks;
    private final ActivityConfig activity;
    private final EarningConfig earning;
    private final HudConfig hud;
    private final ExpiryConfig expiry;
    private final KingdomConfig kingdom;
    private final BorderParticleConfig borderParticles;
    private final TerritoryHudConfig territoryHud;
    private final KingdomGuiConfig kingdomGui;
    private final KingdomBankConfig kingdomBank;
    private final KingdomClaimBlocksConfig kingdomClaimBlocks;
    private final WorldGuardIntegrationConfig worldGuard;

    private DominiumConfig(String databaseType, String sqliteFile, long startingClaimBlocks,
                           ActivityConfig activity, EarningConfig earning, HudConfig hud,
                           ExpiryConfig expiry, KingdomConfig kingdom,
                           BorderParticleConfig borderParticles,
                           TerritoryHudConfig territoryHud, KingdomGuiConfig kingdomGui,
                           KingdomBankConfig kingdomBank,
                           KingdomClaimBlocksConfig kingdomClaimBlocks,
                           WorldGuardIntegrationConfig worldGuard) {
        this.databaseType = Objects.requireNonNull(databaseType, "databaseType");
        this.sqliteFile = Objects.requireNonNull(sqliteFile, "sqliteFile");
        if (startingClaimBlocks < 0) {
            throw new IllegalStateException(
                    "claim-blocks.starting-balance must be >= 0 (got " + startingClaimBlocks + ")");
        }
        this.startingClaimBlocks = startingClaimBlocks;
        this.activity = Objects.requireNonNull(activity);
        this.earning = Objects.requireNonNull(earning);
        this.hud = Objects.requireNonNull(hud);
        this.expiry = Objects.requireNonNull(expiry);
        this.kingdom = Objects.requireNonNull(kingdom);
        this.borderParticles = Objects.requireNonNull(borderParticles);
        this.territoryHud = Objects.requireNonNull(territoryHud);
        this.kingdomGui = Objects.requireNonNull(kingdomGui);
        this.kingdomBank = Objects.requireNonNull(kingdomBank);
        this.kingdomClaimBlocks = Objects.requireNonNull(kingdomClaimBlocks);
        this.worldGuard = Objects.requireNonNull(worldGuard);
    }

    /**
     * Plugin-aware variant: schrijft ontbrekende defaults (bijv. nieuwe
     * particle-keys uit een oudere {@code config.yml}) terug naar het
     * bestand zodat de configuratie meegroeit met de plugin.
     */
    public static DominiumConfig fromPlugin(Plugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        DominiumConfig loaded = fromBukkit(cfg, mutation -> {
            mutation.run();
            plugin.saveConfig();
        });
        return loaded;
    }

    public static DominiumConfig fromBukkit(FileConfiguration cfg) {
        return fromBukkit(cfg, null);
    }

    public static DominiumConfig fromBukkit(FileConfiguration cfg,
                                             java.util.function.Consumer<Runnable> writeBackHook) {
        String databaseType = cfg.getString("database.type", "sqlite").toLowerCase();
        String sqliteFile = cfg.getString("database.sqlite.file", "data.db");
        if (!databaseType.equals("sqlite")) {
            throw new IllegalStateException(
                    "database.type '" + databaseType + "' is not yet supported in this build");
        }
        long startingClaimBlocks = cfg.getLong("claim-blocks.starting-balance", 1000L);
        var activity = new ActivityConfig(
                cfg.getLong("activity.window-seconds", 300L),
                cfg.getLong("activity.flush-interval-seconds", 60L));
        var earning = new EarningConfig(
                cfg.getLong("earning.blocks-per-interval", 4L),
                cfg.getLong("earning.interval-seconds", 300L),
                cfg.getLong("earning.daily-cap", 500L),
                cfg.getLong("earning.tick-seconds", 60L));
        var hud = new HudConfig(cfg.getLong("hud.refresh-millis", 750L));
        var expiry = new ExpiryConfig(
                cfg.getBoolean("expiry.enabled", false),
                cfg.getLong("expiry.inactivity-days", 60L),
                cfg.getLong("expiry.min-age-days", 3L),
                cfg.getLong("expiry.scan-interval-minutes", 60L));
        var kingdom = new KingdomConfig(
                cfg.getInt("kingdom.name-min-length", 3),
                cfg.getInt("kingdom.name-max-length", 24),
                cfg.getLong("kingdom.invite-ttl-minutes", 10L),
                cfg.getLong("kingdom.confirm-ttl-seconds", 30L));
        BorderParticleConfig particles = BorderParticleConfigLoader.load(cfg, LOG, writeBackHook);
        var thud = new TerritoryHudConfig(
                cfg.getBoolean("territory-hud.enabled", true),
                cfg.getLong("territory-hud.refresh-interval-ticks", 20L),
                cfg.getString("territory-hud.wilderness", "<aqua>♣ <white>Wilderness <light_purple>♣"),
                cfg.getString("territory-hud.personal-owner",    "<green><owner>'s claim <gray>(Owner)"),
                cfg.getString("territory-hud.personal-trusted",  "<green><owner>'s claim <gray>(Trusted)"),
                cfg.getString("territory-hud.personal-visitor",  "<green><owner>'s claim <gray>(Visitor)"),
                cfg.getString("territory-hud.personal-public",   "<green><owner>'s claim"),
                cfg.getString("territory-hud.kingdom-leader",    "<green><kingdom> <gray>(Leader)"),
                cfg.getString("territory-hud.kingdom-co-leader", "<green><kingdom> <gray>(Co-Leader)"),
                cfg.getString("territory-hud.kingdom-member",    "<green><kingdom> <gray>(Member)"),
                cfg.getString("territory-hud.kingdom-visitor",   "<green><kingdom> <gray>(Visitor)"),
                cfg.getString("territory-hud.kingdom-public",    "<green><kingdom>"));
        var gui = new KingdomGuiConfig(
                cfg.getBoolean("kingdom-gui.enabled", true),
                cfg.getString("kingdom-gui.main-title", "<dark_green>Kingdom"),
                cfg.getString("kingdom-gui.members-title", "<dark_green>Kingdom Members"),
                cfg.getString("kingdom-gui.invites-title", "<gold>Kingdom Invites"),
                cfg.getString("kingdom-gui.visitors-title", "<aqua>Kingdom Visitors"));
        var bank = new KingdomBankConfig(
                cfg.getBoolean("kingdom-bank.enabled", true),
                dev.ankiesmp.dominium.core.bank.Money.majorToMinor(
                        cfg.getDouble("kingdom-bank.max-balance", 1_000_000_000.00)));
        var cbCfg = new KingdomClaimBlocksConfig(
                cfg.getBoolean("kingdom-claimblocks.contribution-enabled", true),
                cfg.getBoolean("kingdom-claimblocks.purchasing-enabled", true),
                dev.ankiesmp.dominium.core.bank.Money.majorToMinor(
                        cfg.getDouble("kingdom-claimblocks.price-per-block", 10.00)),
                Math.max(1L, cfg.getLong("kingdom-claimblocks.max-purchase-per-operation", 10000L)));
        var wg = new WorldGuardIntegrationConfig(
                cfg.getBoolean("integrations.worldguard.enabled", true),
                cfg.getBoolean("integrations.worldguard.block-claims-in-regions", true),
                cfg.getBoolean("integrations.worldguard.ignore-global-region", true));
        return new DominiumConfig(databaseType, sqliteFile, startingClaimBlocks,
                activity, earning, hud, expiry, kingdom, particles, thud, gui,
                bank, cbCfg, wg);
    }

    /** Test-only fabricator (full). */
    public static DominiumConfig of(String databaseType, String sqliteFile, long startingClaimBlocks,
                                    ActivityConfig activity, EarningConfig earning,
                                    HudConfig hud, ExpiryConfig expiry,
                                    KingdomConfig kingdom, BorderParticleConfig particles,
                                    TerritoryHudConfig thud, KingdomGuiConfig gui) {
        return new DominiumConfig(databaseType, sqliteFile, startingClaimBlocks,
                activity, earning, hud, expiry, kingdom, particles, thud, gui,
                new KingdomBankConfig(true, 100_000_000_000L),
                new KingdomClaimBlocksConfig(true, true, 1000L, 10000L),
                new WorldGuardIntegrationConfig(false, true, true));
    }

    /** Compact fabricator met defaults. */
    public static DominiumConfig of(String databaseType, String sqliteFile, long startingClaimBlocks) {
        return of(databaseType, sqliteFile, startingClaimBlocks,
                new ActivityConfig(300, 60),
                new EarningConfig(0, 300, 0, 60),
                new HudConfig(750),
                new ExpiryConfig(false, 60, 3, 60),
                new KingdomConfig(3, 24, 10, 30),
                new BorderParticleConfig(false, "DUST", 50, 255, 50, 1.0f,
                        5.0, 6.5, 11.0, 0.9, 6L, 60, false, true, 0.25, false),
                TerritoryHudConfig.defaults(),
                new KingdomGuiConfig(true, "<dark_green>Kingdom",
                        "<dark_green>Kingdom Members", "<gold>Kingdom Invites",
                        "<aqua>Kingdom Visitors"));
    }

    public String databaseType() { return databaseType; }
    public String sqliteFile() { return sqliteFile; }
    public long startingClaimBlocks() { return startingClaimBlocks; }
    public ActivityConfig activity() { return activity; }
    public EarningConfig earning() { return earning; }
    public HudConfig hud() { return hud; }
    public ExpiryConfig expiry() { return expiry; }
    public KingdomConfig kingdom() { return kingdom; }
    public BorderParticleConfig borderParticles() { return borderParticles; }
    public TerritoryHudConfig territoryHud() { return territoryHud; }
    public KingdomGuiConfig kingdomGui() { return kingdomGui; }
    public KingdomBankConfig kingdomBank() { return kingdomBank; }
    public KingdomClaimBlocksConfig kingdomClaimBlocks() { return kingdomClaimBlocks; }
    public WorldGuardIntegrationConfig worldGuard() { return worldGuard; }

    public record ActivityConfig(long windowSeconds, long flushIntervalSeconds) {}
    public record EarningConfig(long blocksPerInterval, long intervalSeconds,
                                long dailyCap, long tickSeconds) {}
    public record HudConfig(long refreshMillis) {}
    public record ExpiryConfig(boolean enabled, long inactivityDays,
                               long minAgeDays, long scanIntervalMinutes) {}
    public record KingdomConfig(int nameMinLength, int nameMaxLength,
                                long inviteTtlMinutes, long confirmTtlSeconds) {
        public KingdomConfig {
            if (nameMinLength < 1 || nameMaxLength < nameMinLength) {
                throw new IllegalStateException("invalid kingdom name length bounds");
            }
            if (inviteTtlMinutes < 1) throw new IllegalStateException("invite-ttl must be >= 1m");
            if (confirmTtlSeconds < 1) throw new IllegalStateException("confirm-ttl must be >= 1s");
        }
    }
    public record BorderParticleConfig(boolean enabled, String particle,
                                       int colorRed, int colorGreen, int colorBlue, float size,
                                       double triggerDistance, double hideDistance,
                                       double renderDistance,
                                       double spacing, long updateIntervalTicks,
                                       int maxParticlesPerPlayer, boolean onlyForeignClaims,
                                       boolean terrainFollowing, double verticalOffset,
                                       boolean debug) {
        public BorderParticleConfig {
            if (triggerDistance <= 0 || hideDistance < triggerDistance
                    || renderDistance < hideDistance
                    || spacing <= 0 || updateIntervalTicks <= 0
                    || maxParticlesPerPlayer <= 0 || size <= 0) {
                throw new IllegalStateException("invalid border-particle config");
            }
            if (colorRed < 0 || colorRed > 255
                    || colorGreen < 0 || colorGreen > 255
                    || colorBlue < 0 || colorBlue > 255) {
                throw new IllegalStateException("color RGB must be 0..255");
            }
        }
    }

    public record TerritoryHudConfig(boolean enabled, long refreshIntervalTicks,
                                     String wilderness,
                                     String personalOwner, String personalTrusted,
                                     String personalVisitor, String personalPublic,
                                     String kingdomLeader, String kingdomCoLeader,
                                     String kingdomMember, String kingdomVisitor,
                                     String kingdomPublic) {
        public TerritoryHudConfig {
            if (refreshIntervalTicks < 1) {
                throw new IllegalStateException("territory-hud.refresh-interval-ticks must be >= 1");
            }
            Objects.requireNonNull(wilderness);
            Objects.requireNonNull(personalOwner);
            Objects.requireNonNull(personalTrusted);
            Objects.requireNonNull(personalVisitor);
            Objects.requireNonNull(personalPublic);
            Objects.requireNonNull(kingdomLeader);
            Objects.requireNonNull(kingdomCoLeader);
            Objects.requireNonNull(kingdomMember);
            Objects.requireNonNull(kingdomVisitor);
            Objects.requireNonNull(kingdomPublic);
        }
        public static TerritoryHudConfig defaults() {
            return new TerritoryHudConfig(true, 20L,
                    "<aqua>♣ <white>Wilderness <light_purple>♣",
                    "<green><owner>'s claim <gray>(Owner)",
                    "<green><owner>'s claim <gray>(Trusted)",
                    "<green><owner>'s claim <gray>(Visitor)",
                    "<green><owner>'s claim",
                    "<green><kingdom> <gray>(Leader)",
                    "<green><kingdom> <gray>(Co-Leader)",
                    "<green><kingdom> <gray>(Member)",
                    "<green><kingdom> <gray>(Visitor)",
                    "<green><kingdom>");
        }
    }

    public record KingdomGuiConfig(boolean enabled, String mainTitle, String membersTitle,
                                   String invitesTitle, String visitorsTitle) {
        public KingdomGuiConfig {
            Objects.requireNonNull(mainTitle);
            Objects.requireNonNull(membersTitle);
            Objects.requireNonNull(invitesTitle);
            Objects.requireNonNull(visitorsTitle);
        }
    }

    public record KingdomBankConfig(boolean enabled, long maxBalanceMinor) {
        public KingdomBankConfig {
            if (maxBalanceMinor <= 0) throw new IllegalStateException("max-balance must be > 0");
        }
    }

    public record KingdomClaimBlocksConfig(boolean contributionEnabled, boolean purchasingEnabled,
                                           long pricePerBlockMinor, long maxPurchasePerOperation) {
        public KingdomClaimBlocksConfig {
            if (pricePerBlockMinor <= 0) throw new IllegalStateException("price must be > 0");
            if (maxPurchasePerOperation <= 0) throw new IllegalStateException("max/op must be > 0");
        }
    }

    public record WorldGuardIntegrationConfig(boolean enabled, boolean blockClaimsInRegions,
                                              boolean ignoreGlobalRegion) {}
}
