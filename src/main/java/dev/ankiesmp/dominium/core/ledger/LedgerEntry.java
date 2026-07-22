package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Één geboekte mutatie in de claim-block-ledger. Immutable value object.
 */
public final class LedgerEntry {

    private final long id;
    private final HolderKey holder;
    private final long delta;
    private final ClaimBlockReason reason;
    private final String reference;
    private final UUID idempotencyKey;
    private final String actor;
    private final Instant createdAt;

    public LedgerEntry(
            long id,
            HolderKey holder,
            long delta,
            ClaimBlockReason reason,
            String reference,
            UUID idempotencyKey,
            String actor,
            Instant createdAt) {
        this.id = id;
        this.holder = Objects.requireNonNull(holder, "holder");
        this.delta = delta;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.reference = reference;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.actor = actor;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public long id() { return id; }
    public HolderKey holder() { return holder; }
    public long delta() { return delta; }
    public ClaimBlockReason reason() { return reason; }
    public Optional<String> reference() { return Optional.ofNullable(reference); }
    public UUID idempotencyKey() { return idempotencyKey; }
    public Optional<String> actor() { return Optional.ofNullable(actor); }
    public Instant createdAt() { return createdAt; }
}
