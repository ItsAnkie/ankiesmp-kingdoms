package dev.ankiesmp.dominium.core.earning;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Boekt actieve-speeltijd-beloningen op de ledger. Twee lagen:
 * <ul>
 *   <li>{@link EarningStore#reserveDailyEarning} dwingt de daily cap
 *       atomair af.</li>
 *   <li>Ledger-post gebruikt een deterministische key
 *       {@code active-play-earn:<uuid>:<utc-day>:<slot>} — retries en
 *       crashes kunnen dus nooit dubbel belonen.</li>
 * </ul>
 *
 * <p>Adminmutaties gebruiken {@link ClaimBlockReason#ADMIN_GRANT} en
 * raken deze cap niet aan (aparte reason + aparte key-prefix).
 */
public final class ActivePlayEarner {

    public static final String REFERENCE_PREFIX = "active-play-earn:";

    private final ClaimBlockLedger ledger;
    private final EarningStore store;
    private final EarningConfig config;

    public ActivePlayEarner(ClaimBlockLedger ledger, EarningStore store, EarningConfig config) {
        this.ledger = Objects.requireNonNull(ledger);
        this.store = Objects.requireNonNull(store);
        this.config = Objects.requireNonNull(config);
    }

    public boolean enabled() {
        return config.blocksPerInterval() > 0 && config.intervalSeconds() > 0;
    }

    public EarningConfig config() { return config; }

    public long dayFor(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
    }

    /**
     * Boekt maximaal één interval-worth blocks voor deze speler op deze
     * moment-tijdstamp. Idempotent op ({@code playerId}, {@code intervalSlot}):
     * dezelfde slot boekt nooit twee keer.
     *
     * @param intervalSlot monotoon oplopend nummer (bijv. UTC-seconden / interval).
     */
    public Outcome award(UUID playerId, Instant now, long intervalSlot) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(now);
        if (!enabled()) return Outcome.disabled();

        long day = dayFor(now);
        long requested = config.blocksPerInterval();
        long granted = store.reserveDailyEarning(
                playerId, day, requested, config.dailyCap(), now.toEpochMilli());
        if (granted <= 0) return Outcome.capped(day);

        String reference = REFERENCE_PREFIX + playerId + ":" + day + ":" + intervalSlot;
        UUID key = UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8));

        PostingOutcome outcome = ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(playerId))
                .delta(granted)
                .reason(ClaimBlockReason.ACTIVE_PLAY_EARN)
                .reference(reference)
                .idempotencyKey(key)
                .actor("SYSTEM:active-play")
                .build());
        return switch (outcome.kind()) {
            case APPLIED -> Outcome.awarded(granted, day);
            case ALREADY_APPLIED -> Outcome.duplicate(day);
            case INSUFFICIENT_BALANCE -> throw new IllegalStateException(
                    "positive earn cannot yield INSUFFICIENT_BALANCE");
        };
    }

    public long earnedToday(UUID playerId, Instant now) {
        return store.earnedToday(playerId, dayFor(now));
    }

    public long dailyCapRemaining(UUID playerId, Instant now) {
        return Math.max(0, config.dailyCap() - earnedToday(playerId, now));
    }

    public record Outcome(Kind kind, long grantedBlocks, long activeDay) {
        public enum Kind { AWARDED, DUPLICATE, CAPPED, DISABLED }
        public static Outcome awarded(long blocks, long day) { return new Outcome(Kind.AWARDED, blocks, day); }
        public static Outcome duplicate(long day)            { return new Outcome(Kind.DUPLICATE, 0, day); }
        public static Outcome capped(long day)               { return new Outcome(Kind.CAPPED, 0, day); }
        public static Outcome disabled()                     { return new Outcome(Kind.DISABLED, 0, 0); }
    }

    public record EarningConfig(long blocksPerInterval, long intervalSeconds, long dailyCap) {
        public EarningConfig {
            if (blocksPerInterval < 0 || intervalSeconds < 0 || dailyCap < 0) {
                throw new IllegalArgumentException("earning config values must be >= 0");
            }
        }
    }
}
