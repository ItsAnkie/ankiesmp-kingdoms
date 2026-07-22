package dev.ankiesmp.dominium.core.ledger;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.player.FakePlayerLookup;
import dev.ankiesmp.dominium.core.player.PlayerTargetResolver;
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

/**
 * End-to-end tests voor {@link AdminGrantAction}: koppelt de
 * {@link PlayerTargetResolver} aan een échte SQLite-ledger en verifieert
 * dat afgewezen targets nooit een ledger- of balance-record produceren.
 * Dit is de exacte gedragsregressietest voor de MT-003 bug.
 */
class AdminGrantActionTest {

    private static final UUID RENSJAM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID XTC     = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir Path tempDir;
    private Database database;
    private ClaimBlockLedger ledger;
    private ClaimBlockAdminOps adminOps;
    private FakePlayerLookup lookup;
    private AdminGrantAction action;

    @BeforeEach
    void setUp() {
        Path db = tempDir.resolve("admin-grant.db");
        database = Database.sqlite("jdbc:sqlite:" + db.toAbsolutePath());
        new MigrationRunner(database).migrate();
        ledger = new SqlClaimBlockLedger(database);
        adminOps = new ClaimBlockAdminOps(ledger);
        lookup = new FakePlayerLookup()
                .addOnline(RENSJAM, "RensJAM")
                .addKnownOffline(XTC, "XTC");
        action = new AdminGrantAction(new PlayerTargetResolver(lookup), adminOps);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    // AG-001 — online bekende speler wordt geaccepteerd en krijgt een ledger entry.
    @Test
    void onlineKnownPlayerAccepted() {
        AdminGrantAction.Result r = action.run("RensJAM", 500, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.GRANTED, r.kind());
        assertEquals("RensJAM", r.target().name());
        assertEquals(500L, r.outcome().balance().balance());
        assertEquals(1, countAdminGrantRows(RENSJAM));
    }

    // AG-002 — bekende offline speler (hasPlayedBefore) wordt geaccepteerd.
    @Test
    void knownOfflinePlayerAccepted() {
        AdminGrantAction.Result r = action.run("XTC", 300, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.GRANTED, r.kind());
        assertEquals(300L, r.outcome().balance().balance());
        assertEquals(1, countAdminGrantRows(XTC));
    }

    // AG-003 — onbekende naam wordt geweigerd, geen ledger entry en geen balance record.
    @Test
    void unknownNameRejectedNoLedgerMutation() {
        AdminGrantAction.Result r = action.run("Rens", 999, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.UNKNOWN_PLAYER, r.kind());
        assertEquals(
                "Unknown player. The player must have joined this server before.",
                r.message());
        assertEquals(0, countAllRowsForName("Rens"));
        // 'Rens' bestaat niet als UUID; controleer ook dat RensJAM geen entry heeft opgelopen.
        assertEquals(0, countAdminGrantRows(RENSJAM));
        assertFalse(hasBalanceRow(RENSJAM));
    }

    // AG-004 — willekeurige geldige UUID zonder hasPlayedBefore wordt geweigerd.
    @Test
    void randomUuidRejectedNoLedgerMutation() {
        UUID ghost = UUID.randomUUID();
        AdminGrantAction.Result r = action.run(ghost.toString(), 100, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.UNKNOWN_PLAYER, r.kind());
        assertEquals(0, countAdminGrantRows(ghost));
        assertFalse(hasBalanceRow(ghost));
    }

    // AG-005 — gelijkende naam kiest nooit een verkeerde speler.
    @Test
    void similarPrefixNameDoesNotMatchOther() {
        AdminGrantAction.Result r = action.run("RensJA", 100, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.UNKNOWN_PLAYER, r.kind());
        assertEquals(0, countAdminGrantRows(RENSJAM), "RensJAM mag niet geraakt worden");
        assertFalse(hasBalanceRow(RENSJAM));
    }

    // AG-006 — case-insensitieve exact-match is bewust ondersteund (documented in PlayerTargetResolver).
    @Test
    void caseInsensitiveExactMatchAccepted() {
        AdminGrantAction.Result r = action.run("rensjam", 42, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.GRANTED, r.kind());
        assertEquals("RensJAM", r.target().name());
    }

    // AG-007 — geweigerde target maakt echt niets aan in de balans-tabel.
    @Test
    void rejectedTargetLeavesBalanceTableUntouched() {
        assertFalse(hasBalanceRow(RENSJAM));
        action.run("Rens", 100, "ADMIN:test", UUID.randomUUID());
        action.run(UUID.randomUUID().toString(), 100, "ADMIN:test", UUID.randomUUID());
        action.run("", 100, "ADMIN:test", UUID.randomUUID());
        assertEquals(0, countTotalLedgerRows());
        assertEquals(0, countTotalBalanceRows());
    }

    // AG-008 — negatief bedrag wordt door de action-laag afgevangen, geen ledgermutatie.
    @Test
    void nonPositiveAmountRejected() {
        AdminGrantAction.Result r = action.run("RensJAM", 0, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.INVALID_AMOUNT, r.kind());
        assertEquals(0, countTotalLedgerRows());
        assertEquals(0, countTotalBalanceRows());

        r = action.run("RensJAM", -5, "ADMIN:test", UUID.randomUUID());
        assertEquals(AdminGrantAction.Result.Kind.INVALID_AMOUNT, r.kind());
        assertEquals(0, countTotalLedgerRows());
    }

    private long countAdminGrantRows(UUID uuid) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger " +
                            "WHERE holder_type = 'PLAYER' AND holder_id = ? AND reason = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ClaimBlockReason.ADMIN_GRANT.name());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    private long countAllRowsForName(String needle) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger WHERE actor LIKE ?")) {
                ps.setString(1, "%" + needle + "%");
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    private boolean hasBalanceRow(UUID uuid) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM claim_block_balance " +
                            "WHERE holder_type = 'PLAYER' AND holder_id = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    private long countTotalLedgerRows() {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger");
                 ResultSet rs = ps.executeQuery()) {
                rs.next(); return rs.getLong(1);
            }
        });
    }

    private long countTotalBalanceRows() {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_balance");
                 ResultSet rs = ps.executeQuery()) {
                rs.next(); return rs.getLong(1);
            }
        });
    }
}
