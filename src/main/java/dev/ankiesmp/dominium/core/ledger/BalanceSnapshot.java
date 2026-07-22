package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.core.common.HolderKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Gematerialiseerde saldostand van een holder na een boeking.
 */
public final class BalanceSnapshot {

    private final HolderKey holder;
    private final long balance;
    private final long totalEarned;
    private final long totalSpent;
    private final Instant updatedAt;

    public BalanceSnapshot(HolderKey holder, long balance, long totalEarned, long totalSpent, Instant updatedAt) {
        this.holder = Objects.requireNonNull(holder, "holder");
        if (balance < 0) throw new IllegalArgumentException("balance must be non-negative");
        if (totalEarned < 0) throw new IllegalArgumentException("totalEarned must be non-negative");
        if (totalSpent < 0) throw new IllegalArgumentException("totalSpent must be non-negative");
        this.balance = balance;
        this.totalEarned = totalEarned;
        this.totalSpent = totalSpent;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public HolderKey holder() { return holder; }
    public long balance() { return balance; }
    public long totalEarned() { return totalEarned; }
    public long totalSpent() { return totalSpent; }
    public Instant updatedAt() { return updatedAt; }
}
