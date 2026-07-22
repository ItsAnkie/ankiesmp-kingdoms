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

class KingdomClaimBlockAdminOpsTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimBlockLedger ledger;
    private KingdomClaimBlockAdminOps ops;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("kco.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        ledger = new SqlClaimBlockLedger(db);
        ops = new KingdomClaimBlockAdminOps(ledger);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    // KO-001 — kingdom-grant boekt op de KINGDOM-holder, niet op een speler.
    @Test
    void grantGoesToKingdomHolder() {
        UUID kingdomId = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ops.grantToKingdom(kingdomId, 500, "ADMIN:test");
        assertEquals(500L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
        assertEquals(0L, ledger.balanceOrZero(HolderKey.player(player)).balance(),
                "personal saldo mag nooit betrokken zijn");
    }

    // KO-002 — audit-actor + reference worden bewaard.
    @Test
    void auditFieldsPersisted() {
        UUID kingdomId = UUID.randomUUID();
        ops.grantToKingdom(kingdomId, 100, "ADMIN:mrsmith");
        db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT actor, reason, reference FROM claim_block_ledger " +
                            "WHERE holder_type='KINGDOM' AND holder_id=?")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ADMIN:mrsmith", rs.getString("actor"));
                    assertEquals(ClaimBlockReason.ADMIN_GRANT.name(), rs.getString("reason"));
                    assertTrue(rs.getString("reference").startsWith("admin-grant-kingdom:"));
                }
            }
            return null;
        });
    }

    // KO-003 — expliciete idempotency key maakt het retry-safe.
    @Test
    void explicitKeyIsIdempotent() {
        UUID kingdomId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var first = ops.grantToKingdom(kingdomId, 250, "ADMIN:t", key);
        var second = ops.grantToKingdom(kingdomId, 250, "ADMIN:t", key);
        assertEquals(PostingOutcome.Kind.APPLIED, first.kind());
        assertEquals(PostingOutcome.Kind.ALREADY_APPLIED, second.kind());
        assertEquals(250L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
    }

    // KO-004 — negatief/0 geweigerd.
    @Test
    void nonPositiveRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ops.grantToKingdom(UUID.randomUUID(), 0, "ADMIN:t"));
        assertThrows(IllegalArgumentException.class,
                () -> ops.grantToKingdom(UUID.randomUUID(), -1, "ADMIN:t"));
    }
}
