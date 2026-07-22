package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.storage.bank.SqlBankStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifieert dat recovery PENDING veilig als FAILED markeert en EXTERNAL_APPLIED als COMPENSATION_REQUIRED. */
class BankRecoverySemanticsTest {

    @TempDir Path tempDir;
    private Database db;
    private SqlBankStore store;
    private KingdomBankService svc;
    private UUID kingdomId;
    private UUID leader;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("recovery.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        var ks = new SqlKingdomStore(db);
        var kSvc = new KingdomService(ks, Clock.systemUTC(),
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        leader = UUID.randomUUID();
        kingdomId = kSvc.create("Alpha", leader).value().id();
        store = new SqlBankStore(db);
        svc = new KingdomBankService(store, kSvc, VaultAdapter.NO_OP, Clock.systemUTC(),
                1_000_000_000L);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void seed(UUID id, BankOperation.State state) {
        var op = new BankOperation(id, kingdomId, BankOperation.Kind.DEPOSIT, 500L,
                state, leader, Optional.of("seeded"),
                Instant.now(), Instant.now());
        store.applyBalanceChangeWithJournal(op, 0L, 1_000_000_000L);
    }

    // RS-001 — PENDING → FAILED (nooit blind Vault opnieuw).
    @Test
    void pendingBecomesFailed() {
        UUID id = UUID.randomUUID();
        seed(id, BankOperation.State.PENDING);
        svc.recoverIncompleteOperations();
        assertEquals(BankOperation.State.FAILED,
                store.findOperation(id).orElseThrow().state());
    }

    // RS-002 — EXTERNAL_APPLIED → COMPENSATION_REQUIRED (Vault heeft al iets gedaan).
    @Test
    void externalAppliedBecomesCompensationRequired() {
        UUID id = UUID.randomUUID();
        seed(id, BankOperation.State.EXTERNAL_APPLIED);
        svc.recoverIncompleteOperations();
        assertEquals(BankOperation.State.COMPENSATION_REQUIRED,
                store.findOperation(id).orElseThrow().state());
    }

    // RS-003 — COMMITTED wordt niet aangeraakt.
    @Test
    void committedNotTouched() {
        UUID id = UUID.randomUUID();
        seed(id, BankOperation.State.COMMITTED);
        int touched = svc.recoverIncompleteOperations();
        assertEquals(0, touched);
        assertEquals(BankOperation.State.COMMITTED,
                store.findOperation(id).orElseThrow().state());
    }
}
