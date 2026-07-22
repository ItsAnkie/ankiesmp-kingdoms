package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimBlockLedgerTest {

    @TempDir Path tempDir;
    private Database database;
    private ClaimBlockLedger ledger;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("ledger-test.db");
        database = Database.sqlite("jdbc:sqlite:" + dbFile.toAbsolutePath());
        int applied = new MigrationRunner(database).migrate();
        assertTrue(applied >= 1, "at least the initial ledger migration must apply");
        ledger = new SqlClaimBlockLedger(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    // L-001
    @Test
    void grantIncreasesBalanceByExactDelta() {
        HolderKey holder = HolderKey.player(UUID.randomUUID());
        PostingOutcome outcome = ledger.post(request(holder, 500, ClaimBlockReason.INITIAL_GRANT));

        assertEquals(PostingOutcome.Kind.APPLIED, outcome.kind());
        assertEquals(500L, outcome.balance().balance());
        assertEquals(500L, outcome.balance().totalEarned());
        assertEquals(0L, outcome.balance().totalSpent());
        assertEquals(500L, ledger.balanceOrZero(holder).balance());
    }

    // L-002
    @Test
    void spendFailsWithoutSufficientBalance() {
        HolderKey holder = HolderKey.player(UUID.randomUUID());
        ledger.post(request(holder, 100, ClaimBlockReason.INITIAL_GRANT));

        PostingOutcome outcome = ledger.post(request(holder, -200, ClaimBlockReason.PERSONAL_CLAIM_SPEND));

        assertEquals(PostingOutcome.Kind.INSUFFICIENT_BALANCE, outcome.kind());
        assertEquals(100L, outcome.balance().balance(), "balance must be unchanged");
        assertEquals(100L, ledger.balanceOrZero(holder).balance());
        assertEquals(1, countLedgerRows(holder), "no new ledger row on failed spend");
    }

    // L-003
    @Test
    void duplicateIdempotencyKeyIsNoOp() {
        HolderKey holder = HolderKey.player(UUID.randomUUID());
        UUID key = UUID.randomUUID();

        PostingOutcome first = ledger.post(PostingRequest.builder()
                .holder(holder).delta(300).reason(ClaimBlockReason.EXTERNAL_REWARD)
                .idempotencyKey(key).build());
        assertEquals(PostingOutcome.Kind.APPLIED, first.kind());

        PostingOutcome second = ledger.post(PostingRequest.builder()
                .holder(holder).delta(300).reason(ClaimBlockReason.EXTERNAL_REWARD)
                .idempotencyKey(key).build());

        assertEquals(PostingOutcome.Kind.ALREADY_APPLIED, second.kind());
        assertEquals(300L, second.balance().balance(), "idempotent replay must not double-book");
        assertEquals(300L, ledger.balanceOrZero(holder).balance());
        assertEquals(1, countLedgerRows(holder));
    }

    // L-004 — mixed sequence
    @Test
    void balanceMatchesSumOfDeltas() {
        HolderKey holder = HolderKey.kingdom(UUID.randomUUID());
        long[] deltas = {200, 150, -50, 400, -100, 75};
        long expected = 0;
        for (long d : deltas) {
            expected += d;
            ledger.post(request(holder, d, d > 0 ? ClaimBlockReason.EXTERNAL_REWARD
                    : ClaimBlockReason.KINGDOM_CLAIM_SPEND));
        }
        BalanceSnapshot snap = ledger.balanceOrZero(holder);
        assertEquals(expected, snap.balance());
        long earnedExpected = 0, spentExpected = 0;
        for (long d : deltas) {
            if (d > 0) earnedExpected += d;
            else spentExpected += -d;
        }
        assertEquals(earnedExpected, snap.totalEarned());
        assertEquals(spentExpected, snap.totalSpent());
    }

    // L-005 — property-ish fuzz
    @Test
    void randomSequenceKeepsInvariants() {
        HolderKey holder = HolderKey.player(UUID.randomUUID());
        Random rng = new Random(4242L);
        long balance = 0;
        long earned = 0;
        long spent = 0;

        for (int i = 0; i < 250; i++) {
            long delta = rng.nextInt(2001) - 1000; // -1000..1000
            if (delta == 0) continue;
            PostingOutcome outcome = ledger.post(request(holder, delta,
                    delta > 0 ? ClaimBlockReason.ACTIVE_PLAY_EARN
                              : ClaimBlockReason.PERSONAL_CLAIM_SPEND));
            if (delta < 0 && balance + delta < 0) {
                assertEquals(PostingOutcome.Kind.INSUFFICIENT_BALANCE, outcome.kind());
                assertEquals(balance, outcome.balance().balance());
            } else {
                assertEquals(PostingOutcome.Kind.APPLIED, outcome.kind());
                balance += delta;
                if (delta > 0) earned += delta; else spent += -delta;
                assertEquals(balance, outcome.balance().balance());
                assertTrue(outcome.balance().balance() >= 0);
            }
        }
        BalanceSnapshot snap = ledger.balanceOrZero(holder);
        assertEquals(balance, snap.balance());
        assertEquals(earned, snap.totalEarned());
        assertEquals(spent, snap.totalSpent());
    }

    // L-006
    @Test
    void grantWithZeroDeltaRejectedAtBuild() {
        assertThrows(IllegalArgumentException.class, () -> PostingRequest.builder()
                .holder(HolderKey.player(UUID.randomUUID()))
                .delta(0)
                .reason(ClaimBlockReason.EXTERNAL_REWARD)
                .idempotencyKey(UUID.randomUUID())
                .build());
    }

    // L-007
    @Test
    void balanceReproducibleFromLedger() {
        HolderKey a = HolderKey.player(UUID.randomUUID());
        HolderKey b = HolderKey.player(UUID.randomUUID());
        long[] as = {500, -100, 200, -50};
        long[] bs = {750, -300};

        for (long d : as) {
            ledger.post(request(a, d, d > 0 ? ClaimBlockReason.INITIAL_GRANT
                    : ClaimBlockReason.PERSONAL_CLAIM_SPEND));
        }
        for (long d : bs) {
            ledger.post(request(b, d, d > 0 ? ClaimBlockReason.INITIAL_GRANT
                    : ClaimBlockReason.PERSONAL_CLAIM_SPEND));
        }

        assertEquals(sumOfLedger(a), ledger.balanceOrZero(a).balance());
        assertEquals(sumOfLedger(b), ledger.balanceOrZero(b).balance());
    }

    // L-002 helper — ensure fresh idempotency keys
    private static PostingRequest request(HolderKey holder, long delta, ClaimBlockReason reason) {
        return PostingRequest.builder()
                .holder(holder)
                .delta(delta)
                .reason(reason)
                .idempotencyKey(UUID.randomUUID())
                .build();
    }

    private long countLedgerRows(HolderKey holder) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger WHERE holder_type = ? AND holder_id = ?")) {
                ps.setString(1, holder.type().name());
                ps.setString(2, holder.id().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    private long sumOfLedger(HolderKey holder) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(delta), 0) FROM claim_block_ledger WHERE holder_type = ? AND holder_id = ?")) {
                ps.setString(1, holder.type().name());
                ps.setString(2, holder.id().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    @SuppressWarnings("unused")
    private List<Long> allDeltas(HolderKey holder) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT delta FROM claim_block_ledger WHERE holder_type = ? AND holder_id = ? ORDER BY id")) {
                ps.setString(1, holder.type().name());
                ps.setString(2, holder.id().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> out = new ArrayList<>();
                    while (rs.next()) out.add(rs.getLong(1));
                    return out;
                }
            }
        });
    }
}
