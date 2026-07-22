package dev.ankiesmp.dominium.core.bank;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BankOperation(UUID correlationId, UUID kingdomId, Kind kind, long amountMinor,
                            State state, UUID actor, Optional<String> failureReason,
                            Instant createdAt, Instant updatedAt) {

    public enum Kind { DEPOSIT, WITHDRAW, BUY_CLAIMBLOCKS }

    public enum State {
        PENDING, EXTERNAL_APPLIED, COMMITTED, FAILED,
        COMPENSATED, COMPENSATION_REQUIRED
    }

    public BankOperation {
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(state);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(failureReason);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
