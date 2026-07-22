package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Boekt eenmalig het door {@code claim-blocks.starting-balance} bepaalde
 * startsaldo op de ledger van een nieuwe speler.
 *
 * <p>Idempotency wordt niet in een aparte tabel bijgehouden: de
 * append-only ledger heeft al een {@code UNIQUE}-constraint op
 * {@code idempotency_key}, en die key wordt hier deterministisch afgeleid
 * van de player-UUID:
 *
 * <pre>UUID.nameUUIDFromBytes("initial-player-grant:&lt;player-uuid&gt;")</pre>
 *
 * <p>Gevolgen:
 * <ul>
 *   <li>Reconnect, serverrestart, dubbele join-events en retries na een
 *       DB-fout leveren altijd {@link GrantOutcome.Kind#ALREADY_APPLIED}
 *       op — er wordt nooit dubbel geboekt.</li>
 *   <li>Spelers die vóór de invoering van deze feature al waren gejoined
 *       ontvangen bij hun eerstvolgende join alsnog exact één grant,
 *       omdat er dan simpelweg nog geen ledger-rij met die key bestaat.</li>
 *   <li><b>Bewuste keuze:</b> als een speler op dat moment al een
 *       balans heeft (bijvoorbeeld via {@code ADMIN_GRANT}) maar nog
 *       geen {@code INITIAL_GRANT}, wordt het startsaldo alsnog
 *       toegevoegd. We houden geen aparte "heeft ooit een balans gehad"-
 *       staat bij; dat zou een tweede bron van waarheid introduceren
 *       naast de ledger. Dit is gedocumenteerd in {@code docs/DATABASE.md}.</li>
 * </ul>
 *
 * <p>Bij {@code startingBalance == 0} is de feature volledig uit; er
 * wordt niets naar de ledger geschreven en {@link GrantOutcome.Kind#DISABLED}
 * teruggegeven.
 */
public final class InitialClaimBlockGrant {

    /** Prefix voor zowel de referentie-string als de basis van de idempotency key. */
    public static final String REFERENCE_PREFIX = "initial-player-grant:";
    public static final String ACTOR = "SYSTEM";

    private final ClaimBlockLedger ledger;
    private final long startingBalance;

    public InitialClaimBlockGrant(ClaimBlockLedger ledger, long startingBalance) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (startingBalance < 0) {
            throw new IllegalArgumentException(
                    "startingBalance must be >= 0 (got " + startingBalance + ")");
        }
        this.startingBalance = startingBalance;
    }

    public long startingBalance() { return startingBalance; }

    public boolean enabled() { return startingBalance > 0; }

    /** Deterministische idempotency key voor een speler-UUID. Stabiel over restarts. */
    public static UUID idempotencyKeyFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return UUID.nameUUIDFromBytes(
                (REFERENCE_PREFIX + playerId).getBytes(StandardCharsets.UTF_8));
    }

    public GrantOutcome attemptFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!enabled()) return GrantOutcome.disabled();

        PostingRequest req = PostingRequest.builder()
                .holder(HolderKey.player(playerId))
                .delta(startingBalance)
                .reason(ClaimBlockReason.INITIAL_GRANT)
                .reference(REFERENCE_PREFIX + playerId)
                .idempotencyKey(idempotencyKeyFor(playerId))
                .actor(ACTOR)
                .build();

        PostingOutcome outcome = ledger.post(req);
        return switch (outcome.kind()) {
            case APPLIED -> GrantOutcome.applied(outcome.balance());
            case ALREADY_APPLIED -> GrantOutcome.alreadyApplied(outcome.balance());
            case INSUFFICIENT_BALANCE -> throw new IllegalStateException(
                    "initial grant is always positive; INSUFFICIENT_BALANCE is unreachable");
        };
    }

    public static final class GrantOutcome {
        public enum Kind { APPLIED, ALREADY_APPLIED, DISABLED }

        private final Kind kind;
        private final BalanceSnapshot balance;

        private GrantOutcome(Kind kind, BalanceSnapshot balance) {
            this.kind = kind;
            this.balance = balance;
        }

        public static GrantOutcome applied(BalanceSnapshot balance) {
            return new GrantOutcome(Kind.APPLIED, Objects.requireNonNull(balance));
        }
        public static GrantOutcome alreadyApplied(BalanceSnapshot balance) {
            return new GrantOutcome(Kind.ALREADY_APPLIED, Objects.requireNonNull(balance));
        }
        public static GrantOutcome disabled() {
            return new GrantOutcome(Kind.DISABLED, null);
        }

        public Kind kind() { return kind; }
        /** Nooit {@code null} tenzij {@link #kind()} == {@link Kind#DISABLED}. */
        public BalanceSnapshot balance() { return balance; }
        public boolean applied() { return kind == Kind.APPLIED; }
    }
}
