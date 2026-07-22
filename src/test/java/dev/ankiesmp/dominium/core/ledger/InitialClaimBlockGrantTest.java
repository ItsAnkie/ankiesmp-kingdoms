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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie-suite voor de startgrant. Tests draaien tegen een échte
 * SQLite-DB (via HikariCP, poolsize 1) — exact hetzelfde pad als
 * productie.
 */
class InitialClaimBlockGrantTest {

    @TempDir Path tempDir;
    private Database database;
    private ClaimBlockLedger ledger;

    @BeforeEach
    void setUp() {
        Path db = tempDir.resolve("initial-grant.db");
        database = Database.sqlite("jdbc:sqlite:" + db.toAbsolutePath());
        new MigrationRunner(database).migrate();
        ledger = new SqlClaimBlockLedger(database);
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    // IG-001
    @Test
    void newPlayerGetsExactlyOneStartGrant() {
        var grant = new InitialClaimBlockGrant(ledger, 1000);
        UUID playerId = UUID.randomUUID();

        var first = grant.attemptFor(playerId);

        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.APPLIED, first.kind());
        assertEquals(1000L, first.balance().balance());
        assertEquals(1000L, first.balance().totalEarned());
        assertEquals(0L, first.balance().totalSpent());
        assertEquals(1, countInitialGrantRows(playerId));
    }

    // IG-002
    @Test
    void secondJoinIsIdempotent() {
        var grant = new InitialClaimBlockGrant(ledger, 1000);
        UUID playerId = UUID.randomUUID();

        grant.attemptFor(playerId);
        var second = grant.attemptFor(playerId);

        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.ALREADY_APPLIED, second.kind());
        assertEquals(1000L, second.balance().balance(), "no double booking");
        assertEquals(1, countInitialGrantRows(playerId));
    }

    // IG-003 — restart = nieuwe grant-service instantie op dezelfde DB
    @Test
    void restartDoesNotReGrant() {
        UUID playerId = UUID.randomUUID();
        new InitialClaimBlockGrant(ledger, 1000).attemptFor(playerId);

        // Simuleer restart: nieuwe service met exact hetzelfde starting-balance.
        var afterRestart = new InitialClaimBlockGrant(ledger, 1000);
        var outcome = afterRestart.attemptFor(playerId);

        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.ALREADY_APPLIED, outcome.kind());
        assertEquals(1000L, outcome.balance().balance());
        assertEquals(1, countInitialGrantRows(playerId));
    }

    // IG-004 — twee gelijktijdige joins boeken samen exact één grant
    @Test
    void concurrentInitialisationsResultInExactlyOneGrant() throws Exception {
        var grant = new InitialClaimBlockGrant(ledger, 500);
        UUID playerId = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<InitialClaimBlockGrant.GrantOutcome> f1 =
                    CompletableFuture.supplyAsync(() -> grant.attemptFor(playerId), pool);
            CompletableFuture<InitialClaimBlockGrant.GrantOutcome> f2 =
                    CompletableFuture.supplyAsync(() -> grant.attemptFor(playerId), pool);

            var r1 = f1.get(5, TimeUnit.SECONDS);
            var r2 = f2.get(5, TimeUnit.SECONDS);

            List<InitialClaimBlockGrant.GrantOutcome.Kind> kinds =
                    List.of(r1.kind(), r2.kind());
            assertTrue(kinds.contains(InitialClaimBlockGrant.GrantOutcome.Kind.APPLIED),
                    "one thread must APPLY");
            assertTrue(kinds.contains(InitialClaimBlockGrant.GrantOutcome.Kind.ALREADY_APPLIED),
                    "other thread must see ALREADY_APPLIED");
            assertEquals(500L, ledger.balanceOrZero(HolderKey.player(playerId)).balance(),
                    "balance must reflect a single grant");
            assertEquals(1, countInitialGrantRows(playerId));
        } finally {
            pool.shutdownNow();
        }
    }

    // IG-005 — startsaldo 0 schakelt de grant uit
    @Test
    void zeroStartingBalanceDisablesGrant() {
        var grant = new InitialClaimBlockGrant(ledger, 0);
        UUID playerId = UUID.randomUUID();

        assertFalse(grant.enabled());
        var outcome = grant.attemptFor(playerId);

        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.DISABLED, outcome.kind());
        assertNull(outcome.balance(), "disabled outcome heeft geen balans");
        assertEquals(0L, ledger.balanceOrZero(HolderKey.player(playerId)).balance());
        assertEquals(0, countInitialGrantRows(playerId), "geen ledger-rij bij DISABLED");
    }

    /**
     * IG-006 — Bewuste keuze: spelers die al een saldo hebben (bijv. via
     * ADMIN_GRANT) maar nog geen INITIAL_GRANT ontvingen, krijgen alsnog
     * exact één startgrant bij hun eerstvolgende join. Dit borgt requirement
     * §9 (retroactieve dekking) zonder een tweede bron van waarheid naast
     * de ledger te introduceren.
     */
    @Test
    void existingBalanceWithoutInitialGrantStillReceivesInitialGrant() {
        UUID playerId = UUID.randomUUID();
        HolderKey holder = HolderKey.player(playerId);

        // Pre-existing admin grant zonder INITIAL_GRANT-entry.
        ledger.post(PostingRequest.builder()
                .holder(holder)
                .delta(250)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID())
                .actor("ADMIN:seed")
                .build());
        assertEquals(250L, ledger.balanceOrZero(holder).balance());

        var grant = new InitialClaimBlockGrant(ledger, 1000);
        var first = grant.attemptFor(playerId);
        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.APPLIED, first.kind());
        assertEquals(1250L, first.balance().balance(),
                "initial grant stacks on top of existing balance");

        // En daarna blijft de grant idempotent.
        var second = grant.attemptFor(playerId);
        assertEquals(InitialClaimBlockGrant.GrantOutcome.Kind.ALREADY_APPLIED, second.kind());
        assertEquals(1250L, second.balance().balance());
        assertEquals(1, countInitialGrantRows(playerId));
    }

    // IG-007 — de idempotency key is deterministisch af te leiden van de player-UUID.
    @Test
    void idempotencyKeyIsDeterministicPerPlayer() {
        UUID player = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
        UUID k1 = InitialClaimBlockGrant.idempotencyKeyFor(player);
        UUID k2 = InitialClaimBlockGrant.idempotencyKeyFor(player);
        assertEquals(k1, k2);
        assertNotEquals(k1, InitialClaimBlockGrant.idempotencyKeyFor(UUID.randomUUID()));
    }

    // IG-008 — starting-balance mag niet negatief zijn.
    @Test
    void negativeStartingBalanceRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new InitialClaimBlockGrant(ledger, -1));
    }

    private long countInitialGrantRows(UUID playerId) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger " +
                            "WHERE holder_type = ? AND holder_id = ? AND reason = ?")) {
                ps.setString(1, "PLAYER");
                ps.setString(2, playerId.toString());
                ps.setString(3, ClaimBlockReason.INITIAL_GRANT.name());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }
}
