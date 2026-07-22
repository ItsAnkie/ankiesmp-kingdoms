package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Aanvraag om een enkele mutatie op de claim-block-ledger te boeken.
 * Immutable — retries hergebruiken dezelfde instance zodat de
 * idempotency-key gegarandeerd identiek is.
 */
public final class PostingRequest {

    private final HolderKey holder;
    private final long delta;
    private final ClaimBlockReason reason;
    private final String reference;
    private final UUID idempotencyKey;
    private final String actor;

    private PostingRequest(Builder b) {
        this.holder = Objects.requireNonNull(b.holder, "holder");
        this.reason = Objects.requireNonNull(b.reason, "reason");
        this.idempotencyKey = Objects.requireNonNull(b.idempotencyKey, "idempotencyKey");
        if (b.delta == 0) {
            throw new IllegalArgumentException("delta must be non-zero");
        }
        this.delta = b.delta;
        this.reference = b.reference;
        this.actor = b.actor;
    }

    public static Builder builder() { return new Builder(); }

    public HolderKey holder() { return holder; }
    public long delta() { return delta; }
    public ClaimBlockReason reason() { return reason; }
    public String reference() { return reference; }
    public UUID idempotencyKey() { return idempotencyKey; }
    public String actor() { return actor; }

    public static final class Builder {
        private HolderKey holder;
        private long delta;
        private ClaimBlockReason reason;
        private String reference;
        private UUID idempotencyKey;
        private String actor;

        public Builder holder(HolderKey holder) { this.holder = holder; return this; }
        public Builder delta(long delta) { this.delta = delta; return this; }
        public Builder reason(ClaimBlockReason reason) { this.reason = reason; return this; }
        public Builder reference(String reference) { this.reference = reference; return this; }
        public Builder idempotencyKey(UUID key) { this.idempotencyKey = key; return this; }
        public Builder actor(String actor) { this.actor = actor; return this; }

        public PostingRequest build() { return new PostingRequest(this); }
    }
}
