package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Admin-facing operations op de claim-block ledger. Wordt door
 * {@code /dominium claimblocks} aangeroepen en is bewust een dunne
 * wrapper rond {@link ClaimBlockLedger}: geen eigen staat, geen aparte
 * bookkeeping, alleen validatie + een reproducible builder.
 *
 * <p>Iedere admin-grant krijgt standaard een fris-random idempotency key
 * ({@link #grantToPlayer(UUID, long, String)}). Callers die retry-safe
 * willen zijn kunnen een eigen key doorgeven via
 * {@link #grantToPlayer(UUID, long, String, UUID)}.
 */
public final class ClaimBlockAdminOps {

    public static final String REFERENCE_PREFIX = "admin-grant:";

    private final ClaimBlockLedger ledger;

    public ClaimBlockAdminOps(ClaimBlockLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public PostingOutcome grantToPlayer(UUID playerId, long amount, String actor) {
        return grantToPlayer(playerId, amount, actor, UUID.randomUUID());
    }

    public PostingOutcome grantToPlayer(UUID playerId, long amount, String actor, UUID idempotencyKey) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive (got " + amount + ")");
        }
        PostingRequest req = PostingRequest.builder()
                .holder(HolderKey.player(playerId))
                .delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .reference(REFERENCE_PREFIX + playerId)
                .idempotencyKey(idempotencyKey)
                .actor(actor)
                .build();
        return ledger.post(req);
    }
}
