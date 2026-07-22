package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Admin-facing operation die claimblocks aan een kingdom-holder toekent.
 * Gebruikt bewust dezelfde ledger als personal grants — geen aparte pool
 * of fake bank. Voor development/admin gebruik; fase 5 vervangt dit door
 * de volledige kingdom claimblock pool + kingdom bank.
 */
public final class KingdomClaimBlockAdminOps {

    public static final String REFERENCE_PREFIX = "admin-grant-kingdom:";

    private final ClaimBlockLedger ledger;

    public KingdomClaimBlockAdminOps(ClaimBlockLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger);
    }

    public PostingOutcome grantToKingdom(UUID kingdomId, long amount, String actor) {
        return grantToKingdom(kingdomId, amount, actor, UUID.randomUUID());
    }

    public PostingOutcome grantToKingdom(UUID kingdomId, long amount, String actor,
                                         UUID idempotencyKey) {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(idempotencyKey);
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive (got " + amount + ")");
        }
        PostingRequest req = PostingRequest.builder()
                .holder(HolderKey.kingdom(kingdomId))
                .delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .reference(REFERENCE_PREFIX + kingdomId)
                .idempotencyKey(idempotencyKey)
                .actor(actor)
                .build();
        return ledger.post(req);
    }
}
