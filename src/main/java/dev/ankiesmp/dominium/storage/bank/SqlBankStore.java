package dev.ankiesmp.dominium.storage.bank;

import dev.ankiesmp.dominium.core.bank.BankOperation;
import dev.ankiesmp.dominium.core.bank.BankStore;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqlBankStore implements BankStore {

    private final Database db;

    public SqlBankStore(Database db) { this.db = Objects.requireNonNull(db); }

    @Override
    public long balance(UUID kingdomId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT balance_minor FROM kingdom_bank WHERE kingdom_id = ?")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        });
    }

    @Override
    public boolean applyBalanceChangeWithJournal(BankOperation op, long amountDelta, long maxBalance) {
        return db.withTransaction(conn -> {
            long current = 0L;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT balance_minor FROM kingdom_bank WHERE kingdom_id = ?")) {
                ps.setString(1, op.kingdomId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) current = rs.getLong(1);
                }
            }
            long next = current + amountDelta;
            if (next < 0) return false;
            if (next > maxBalance) return false;
            long now = op.updatedAt().toEpochMilli();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdom_bank(kingdom_id, balance_minor, updated_at) VALUES (?, ?, ?) " +
                            "ON CONFLICT(kingdom_id) DO UPDATE SET balance_minor = excluded.balance_minor, " +
                            "updated_at = excluded.updated_at")) {
                ps.setString(1, op.kingdomId().toString());
                ps.setLong(2, next);
                ps.setLong(3, now);
                ps.executeUpdate();
            }
            upsertJournal(conn, op);
            return true;
        });
    }

    /** Nested-transaction-safe: mag alleen binnen een bestaande {@code withTransaction} lopen. */
    private static void upsertJournal(java.sql.Connection conn, BankOperation op)
            throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bank_operation_journal(correlation_id, kingdom_id, kind, amount_cents, " +
                        "state, reason, actor, external_ref, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?) " +
                        "ON CONFLICT(correlation_id) DO UPDATE SET " +
                        "  state = excluded.state, updated_at = excluded.updated_at, " +
                        "  reason = excluded.reason")) {
            ps.setString(1, op.correlationId().toString());
            ps.setString(2, op.kingdomId().toString());
            ps.setString(3, op.kind().name());
            ps.setLong(4, op.amountMinor());
            ps.setString(5, op.state().name());
            ps.setString(6, op.failureReason().orElse(op.kind().name()));
            ps.setString(7, op.actor().toString());
            ps.setLong(8, op.createdAt().toEpochMilli());
            ps.setLong(9, op.updatedAt().toEpochMilli());
            ps.executeUpdate();
        }
    }

    @Override
    public void updateOperationState(UUID correlationId, BankOperation.State newState,
                                     String failureReason, long updatedAtEpochMillis) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE bank_operation_journal SET state = ?, updated_at = ?, reason = ? " +
                            "WHERE correlation_id = ?")) {
                ps.setString(1, newState.name());
                ps.setLong(2, updatedAtEpochMillis);
                ps.setString(3, failureReason == null ? newState.name() : failureReason);
                ps.setString(4, correlationId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public boolean atomicDebitWithLedger(BankOperation op, long amountMinor, long maxBalance,
                                         LedgerPostInTx ledgerCallback) {
        if (amountMinor <= 0) return false;
        try {
            return db.withTransaction(conn -> {
                long current = 0L;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT balance_minor FROM kingdom_bank WHERE kingdom_id = ?")) {
                    ps.setString(1, op.kingdomId().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) current = rs.getLong(1);
                    }
                }
                long next = current - amountMinor;
                if (next < 0 || next > maxBalance) throw new RollbackSignal();
                long now = op.updatedAt().toEpochMilli();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO kingdom_bank(kingdom_id, balance_minor, updated_at) VALUES (?, ?, ?) " +
                                "ON CONFLICT(kingdom_id) DO UPDATE SET balance_minor = excluded.balance_minor, " +
                                "updated_at = excluded.updated_at")) {
                    ps.setString(1, op.kingdomId().toString());
                    ps.setLong(2, next);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
                upsertJournal(conn, op);
                // Callback binnen dezelfde tx. Bij false → rollback via sentinel.
                if (!ledgerCallback.apply(conn)) throw new RollbackSignal();
                return Boolean.TRUE;
            });
        } catch (RollbackSignal rs) {
            return false;
        }
    }

    /** Sentinel: forceert Database#withTransaction rollback zonder een echte fout te loggen. */
    private static final class RollbackSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;
        RollbackSignal() { super(null, null, false, false); }
    }

    @Override
    public Optional<BankOperation> findOperation(UUID correlationId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT correlation_id, kingdom_id, kind, amount_cents, state, reason, actor, " +
                            "created_at, updated_at FROM bank_operation_journal WHERE correlation_id = ?")) {
                ps.setString(1, correlationId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.<BankOperation>empty();
                }
            }
        });
    }

    @Override
    public List<BankOperation> listIncomplete() {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT correlation_id, kingdom_id, kind, amount_cents, state, reason, actor, " +
                            "created_at, updated_at FROM bank_operation_journal " +
                            "WHERE state NOT IN ('COMMITTED','FAILED','COMPENSATED') ORDER BY created_at")) {
                try (ResultSet rs = ps.executeQuery()) {
                    List<BankOperation> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    @Override
    public List<BankOperation> listForKingdom(UUID kingdomId, int limit) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT correlation_id, kingdom_id, kind, amount_cents, state, reason, actor, " +
                            "created_at, updated_at FROM bank_operation_journal " +
                            "WHERE kingdom_id = ? ORDER BY updated_at DESC LIMIT ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    List<BankOperation> out = new ArrayList<>();
                    while (rs.next()) out.add(read(rs));
                    return out;
                }
            }
        });
    }

    private static BankOperation read(ResultSet rs) throws java.sql.SQLException {
        String reason = rs.getString(6);
        String actor = rs.getString(7);
        return new BankOperation(
                UUID.fromString(rs.getString(1)),
                UUID.fromString(rs.getString(2)),
                BankOperation.Kind.valueOf(mapKind(rs.getString(3))),
                rs.getLong(4),
                BankOperation.State.valueOf(mapState(rs.getString(5))),
                actor == null ? new UUID(0, 0) : UUID.fromString(actor),
                reason == null ? Optional.empty() : Optional.of(reason),
                Instant.ofEpochMilli(rs.getLong(8)),
                Instant.ofEpochMilli(rs.getLong(9)));
    }

    /** V2-schema gebruikt oudere kind-namen; deze mapping normaliseert. */
    private static String mapKind(String raw) {
        // Nieuwe fase-5 waarden zijn direct compatible; oudere V2-namen als
        // ESCROW_IN/PAYOUT komen in fase 5 nog niet voor.
        return raw;
    }

    private static String mapState(String raw) {
        return switch (raw) {
            case "PENDING_EXTERNAL" -> "PENDING";
            default -> raw;
        };
    }
}
