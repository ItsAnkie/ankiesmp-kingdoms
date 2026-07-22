package dev.ankiesmp.dominium.storage.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockHolderType;
import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.ledger.BalanceSnapshot;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.LedgerEntry;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.db.DatabaseException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-implementatie van de {@link ClaimBlockLedger}. Werkt tegen SQLite
 * (en later PostgreSQL) via HikariCP. Alle mutaties draaien in één
 * transactie die zowel de ledger-insert als de gematerialiseerde saldo-
 * update afhandelt.
 */
public final class SqlClaimBlockLedger implements ClaimBlockLedger {

    private final Database database;

    public SqlClaimBlockLedger(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public PostingOutcome post(PostingRequest request) {
        Objects.requireNonNull(request, "request");
        return database.withTransaction(conn -> postInternal(conn, request));
    }

    @Override
    public dev.ankiesmp.dominium.core.ledger.TransferOutcome atomicTransfer(
            PostingRequest debit, PostingRequest credit) {
        Objects.requireNonNull(debit); Objects.requireNonNull(credit);
        if (debit.delta() >= 0) throw new IllegalArgumentException("debit must be negative");
        if (credit.delta() <= 0) throw new IllegalArgumentException("credit must be positive");
        return database.withTransaction(conn -> {
            PostingOutcome d = postInternal(conn, debit);
            if (d.kind() == PostingOutcome.Kind.INSUFFICIENT_BALANCE) {
                return dev.ankiesmp.dominium.core.ledger.TransferOutcome.insufficient(d.balance());
            }
            PostingOutcome c = postInternal(conn, credit);
            if (d.kind() == PostingOutcome.Kind.ALREADY_APPLIED
                    && c.kind() == PostingOutcome.Kind.ALREADY_APPLIED) {
                return dev.ankiesmp.dominium.core.ledger.TransferOutcome
                        .alreadyApplied(d.balance(), c.balance());
            }
            return dev.ankiesmp.dominium.core.ledger.TransferOutcome
                    .applied(d.balance(), c.balance());
        });
    }

    /**
     * Voert een post uit binnen een reeds actieve Connection. Wordt gebruikt door
     * {@code SqlBankStore#atomicDebitWithLedger} om bank-debit + ledger-credit
     * in exact één transactie te combineren.
     */
    public PostingOutcome postInConnection(Connection conn, PostingRequest req) throws SQLException {
        return postInternal(conn, req);
    }

    private PostingOutcome postInternal(Connection conn, PostingRequest req) throws SQLException {
        // 1) Idempotency-check: bestaat er al een entry met deze key?
        LedgerEntry existing = findByIdempotencyKey(conn, req.idempotencyKey());
        if (existing != null) {
            BalanceSnapshot current = loadBalance(conn, existing.holder())
                    .orElseThrow(() -> new DatabaseException(
                            "ledger entry exists without matching balance row"));
            return PostingOutcome.alreadyApplied(current, existing);
        }

        // 2) Balans-check bij spend.
        BalanceSnapshot before = loadBalance(conn, req.holder()).orElse(null);
        long currentBalance = before == null ? 0L : before.balance();
        if (req.delta() < 0 && currentBalance + req.delta() < 0) {
            return PostingOutcome.insufficientBalance(
                    before == null ? emptySnapshot(req.holder()) : before);
        }

        long now = System.currentTimeMillis();
        long newBalance = Math.addExact(currentBalance, req.delta());
        long newEarned = (before == null ? 0L : before.totalEarned())
                + (req.delta() > 0 ? req.delta() : 0L);
        long newSpent = (before == null ? 0L : before.totalSpent())
                + (req.delta() < 0 ? -req.delta() : 0L);

        long insertedId = insertLedger(conn, req, now);
        upsertBalance(conn, req.holder(), newBalance, newEarned, newSpent, now);

        BalanceSnapshot after = new BalanceSnapshot(
                req.holder(), newBalance, newEarned, newSpent, Instant.ofEpochMilli(now));
        LedgerEntry entry = new LedgerEntry(
                insertedId,
                req.holder(),
                req.delta(),
                req.reason(),
                req.reference(),
                req.idempotencyKey(),
                req.actor(),
                Instant.ofEpochMilli(now));
        return PostingOutcome.applied(after, entry);
    }

    @Override
    public Optional<BalanceSnapshot> balance(HolderKey holder) {
        Objects.requireNonNull(holder, "holder");
        return database.withConnection(conn -> loadBalance(conn, holder));
    }

    @Override
    public BalanceSnapshot balanceOrZero(HolderKey holder) {
        return balance(holder).orElseGet(() -> emptySnapshot(holder));
    }

    private static BalanceSnapshot emptySnapshot(HolderKey holder) {
        return new BalanceSnapshot(holder, 0L, 0L, 0L, Instant.EPOCH);
    }

    private static long insertLedger(Connection conn, PostingRequest req, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_block_ledger " +
                        "(holder_type, holder_id, delta, reason, reference, idempotency_key, actor, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, req.holder().type().name());
            ps.setString(2, req.holder().id().toString());
            ps.setLong(3, req.delta());
            ps.setString(4, req.reason().name());
            if (req.reference() == null) ps.setNull(5, java.sql.Types.VARCHAR);
            else ps.setString(5, req.reference());
            ps.setString(6, req.idempotencyKey().toString());
            if (req.actor() == null) ps.setNull(7, java.sql.Types.VARCHAR);
            else ps.setString(7, req.actor());
            ps.setLong(8, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
                return -1L;
            }
        }
    }

    private static void upsertBalance(Connection conn, HolderKey holder,
                                      long balance, long earned, long spent, long updatedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_block_balance " +
                        "(holder_type, holder_id, balance, total_earned, total_spent, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(holder_type, holder_id) DO UPDATE SET " +
                        "  balance = excluded.balance, " +
                        "  total_earned = excluded.total_earned, " +
                        "  total_spent = excluded.total_spent, " +
                        "  updated_at = excluded.updated_at")) {
            ps.setString(1, holder.type().name());
            ps.setString(2, holder.id().toString());
            ps.setLong(3, balance);
            ps.setLong(4, earned);
            ps.setLong(5, spent);
            ps.setLong(6, updatedAt);
            ps.executeUpdate();
        }
    }

    private static Optional<BalanceSnapshot> loadBalance(Connection conn, HolderKey holder) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT balance, total_earned, total_spent, updated_at " +
                        "FROM claim_block_balance WHERE holder_type = ? AND holder_id = ?")) {
            ps.setString(1, holder.type().name());
            ps.setString(2, holder.id().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new BalanceSnapshot(
                        holder,
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getLong(3),
                        Instant.ofEpochMilli(rs.getLong(4))));
            }
        }
    }

    private static LedgerEntry findByIdempotencyKey(Connection conn, UUID key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, holder_type, holder_id, delta, reason, reference, actor, created_at " +
                        "FROM claim_block_ledger WHERE idempotency_key = ?")) {
            ps.setString(1, key.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                HolderKey holder = HolderKey.of(
                        ClaimBlockHolderType.valueOf(rs.getString(2)),
                        UUID.fromString(rs.getString(3)));
                return new LedgerEntry(
                        rs.getLong(1),
                        holder,
                        rs.getLong(4),
                        ClaimBlockReason.valueOf(rs.getString(5)),
                        rs.getString(6),
                        key,
                        rs.getString(7),
                        Instant.ofEpochMilli(rs.getLong(8)));
            }
        }
    }
}
