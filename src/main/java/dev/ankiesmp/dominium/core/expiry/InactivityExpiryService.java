package dev.ankiesmp.dominium.core.expiry;

import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Veilige inactivity-expiry voor persoonlijke claims.
 *
 * <p>Regels:
 * <ul>
 *   <li>alleen claims van type {@link ClaimType#PERSONAL};</li>
 *   <li>owner niet online (te bepalen via de {@code onlinePredicate});</li>
 *   <li>owner's {@code lastActiveAt} ouder dan {@code inactivityThresholdMillis};</li>
 *   <li>claim minimaal {@code minAgeMillis} oud (voorkomt "net gemaakt en weg");</li>
 *   <li>daadwerkelijke verwijdering delegeert aan {@link ClaimService#delete}
 *       zodat ledger-refund en index/cache invalidation via het bestaande
 *       transactionele pad lopen.</li>
 * </ul>
 *
 * <p>Deze service scant en verwijdert alleen; de scheduler (Bukkit) roept
 * {@link #scanAndExpire} periodiek op de db-executor aan.
 */
public final class InactivityExpiryService {

    private final ClaimIndex index;
    private final ClaimService claimService;
    private final PlayerActivityStore activityStore;
    private final Clock clock;
    private final Predicate<UUID> onlinePredicate;
    private final Config config;
    private final Consumer<UUID> postDeleteHook;

    public InactivityExpiryService(ClaimIndex index,
                                   ClaimService claimService,
                                   PlayerActivityStore activityStore,
                                   Clock clock,
                                   Predicate<UUID> onlinePredicate,
                                   Config config) {
        this(index, claimService, activityStore, clock, onlinePredicate, config, id -> {});
    }

    public InactivityExpiryService(ClaimIndex index,
                                   ClaimService claimService,
                                   PlayerActivityStore activityStore,
                                   Clock clock,
                                   Predicate<UUID> onlinePredicate,
                                   Config config,
                                   Consumer<UUID> postDeleteHook) {
        this.index = Objects.requireNonNull(index);
        this.claimService = Objects.requireNonNull(claimService);
        this.activityStore = Objects.requireNonNull(activityStore);
        this.clock = Objects.requireNonNull(clock);
        this.onlinePredicate = Objects.requireNonNull(onlinePredicate);
        this.config = Objects.requireNonNull(config);
        this.postDeleteHook = Objects.requireNonNull(postDeleteHook);
    }

    public boolean enabled() { return config.enabled(); }

    public Report scanAndExpire() {
        if (!config.enabled()) return Report.empty();
        long now = clock.millis();
        List<Claim> snapshot = new ArrayList<>(index.all());
        Set<UUID> checkedOwners = new HashSet<>();
        List<UUID> expired = new ArrayList<>();
        int skipped = 0;

        for (Claim claim : snapshot) {
            if (claim.owner().type() != ClaimType.PERSONAL) { skipped++; continue; }
            if ((now - claim.createdAt().toEpochMilli()) < config.minAgeMillis()) { skipped++; continue; }

            UUID owner = claim.owner().id();
            if (onlinePredicate.test(owner)) { skipped++; continue; }

            // Cache owner-lastActive lookups tijdens deze scan.
            checkedOwners.add(owner);
            Optional<PlayerActivityStore.ActivitySnapshot> act = activityStore.load(owner);
            long lastActive = act.map(PlayerActivityStore.ActivitySnapshot::lastActiveAtEpochMillis)
                    .orElse(claim.createdAt().toEpochMilli());
            if ((now - lastActive) < config.inactivityThresholdMillis()) { skipped++; continue; }

            // Delegeer naar ClaimService.delete: doet ledger-refund + index-remove.
            claimService.delete(claim.id(), deterministicKey(claim.id(), lastActive));
            postDeleteHook.accept(claim.id());
            expired.add(claim.id());
        }
        return new Report(expired, skipped, checkedOwners.size());
    }

    private static UUID deterministicKey(UUID claimId, long lastActive) {
        return UUID.nameUUIDFromBytes(
                ("inactivity-expiry:" + claimId + ":" + lastActive)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public record Report(List<UUID> expired, int skipped, int ownersChecked) {
        public static Report empty() { return new Report(List.of(), 0, 0); }
        public int expiredCount() { return expired.size(); }
    }

    public record Config(boolean enabled,
                         long inactivityThresholdMillis,
                         long minAgeMillis) {
        public Config {
            if (inactivityThresholdMillis < 0 || minAgeMillis < 0) {
                throw new IllegalArgumentException("thresholds must be >= 0");
            }
        }
        public static Config disabled() { return new Config(false, 0, 0); }
    }
}
