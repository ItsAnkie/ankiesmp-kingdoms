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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimBlockAdminOpsTest {

    @TempDir Path tempDir;
    private Database database;
    private ClaimBlockLedger ledger;
    private ClaimBlockAdminOps ops;

    @BeforeEach
    void setUp() {
        Path db = tempDir.resolve("admin-ops.db");
        database = Database.sqlite("jdbc:sqlite:" + db.toAbsolutePath());
        new MigrationRunner(database).migrate();
        ledger = new SqlClaimBlockLedger(database);
        ops = new ClaimBlockAdminOps(ledger);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    // AO-001
    @Test
    void positiveAmountAppliesViaLedger() {
        UUID player = UUID.randomUUID();
        PostingOutcome outcome = ops.grantToPlayer(player, 250, "ADMIN:test");

        assertEquals(PostingOutcome.Kind.APPLIED, outcome.kind());
        assertEquals(250L, outcome.balance().balance());
        assertEquals(1, countRows(player, ClaimBlockReason.ADMIN_GRANT));
        assertEquals(250L, ledger.balanceOrZero(HolderKey.player(player)).balance());
    }

    // AO-002
    @Test
    void zeroOrNegativeAmountRejected() {
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> ops.grantToPlayer(player, 0, "ADMIN:test"));
        assertThrows(IllegalArgumentException.class,
                () -> ops.grantToPlayer(player, -5, "ADMIN:test"));
        assertEquals(0L, ledger.balanceOrZero(HolderKey.player(player)).balance());
    }

    // AO-003 — herhaalde calls zonder expliciete key stacken (elk krijgt fresh random key).
    @Test
    void distinctInvocationsWithoutExplicitKeyStack() {
        UUID player = UUID.randomUUID();
        ops.grantToPlayer(player, 100, "ADMIN:test");
        ops.grantToPlayer(player, 100, "ADMIN:test");
        ops.grantToPlayer(player, 100, "ADMIN:test");

        assertEquals(300L, ledger.balanceOrZero(HolderKey.player(player)).balance());
        assertEquals(3, countRows(player, ClaimBlockReason.ADMIN_GRANT));
    }

    // AO-004 — explicit idempotency key maakt de call retry-safe.
    @Test
    void explicitIdempotencyKeyIsIdempotent() {
        UUID player = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var first = ops.grantToPlayer(player, 500, "ADMIN:test", key);
        var second = ops.grantToPlayer(player, 500, "ADMIN:test", key);

        assertEquals(PostingOutcome.Kind.APPLIED, first.kind());
        assertEquals(PostingOutcome.Kind.ALREADY_APPLIED, second.kind());
        assertEquals(500L, ledger.balanceOrZero(HolderKey.player(player)).balance());
        assertEquals(1, countRows(player, ClaimBlockReason.ADMIN_GRANT));
    }

    // AO-005 — nulls of null-actor worden geweigerd.
    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class,
                () -> ops.grantToPlayer(null, 100, "ADMIN:test"));
        assertThrows(NullPointerException.class,
                () -> ops.grantToPlayer(UUID.randomUUID(), 100, null));
    }

    private long countRows(UUID playerId, ClaimBlockReason reason) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger " +
                            "WHERE holder_type = ? AND holder_id = ? AND reason = ?")) {
                ps.setString(1, "PLAYER");
                ps.setString(2, playerId.toString());
                ps.setString(3, reason.name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }
}
