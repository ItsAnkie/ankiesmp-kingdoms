package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.claim.SqlClaimRepository;
import dev.ankiesmp.dominium.storage.claim.SqlClaimStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie voor CLAIM_REPAIR_MODE: alleen conflicterende owners worden
 * geblokkeerd; andere spelers/kingdoms kunnen normaal doorwerken.
 */
class ClaimMutationGuardTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimIndex index;
    private ClaimBlockLedger ledger;
    private SqlClaimRepository repo;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("guard.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        // Bewust GEEN SingleClaimIndexInstaller.install → simuleert repair-mode DB.
        ledger = new SqlClaimBlockLedger(db);
        repo = new SqlClaimRepository(db);
        index = new ClaimIndex();
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private ClaimService serviceWith(ClaimMutationGuard guard) {
        var validator = new PlacementValidator(index, PlacementRules.defaults());
        return new ClaimService(index, validator, ledger, new SqlClaimStore(repo), guard);
    }

    private void grant(HolderKey holder, long amount) {
        ledger.post(PostingRequest.builder()
                .holder(holder).delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID())
                .build());
    }

    // MG-001 — geblokkeerde owner krijgt BLOCKED bij create.
    @Test
    void blockedOwnerCreateIsRejected() {
        UUID blocked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        grant(HolderKey.player(blocked), 10_000);
        grant(HolderKey.player(free), 10_000);
        var guard = new ClaimMutationGuard.Builder()
                .block(ClaimType.PERSONAL, blocked).build();
        var svc = serviceWith(guard);
        var world = new WorldRef(UUID.randomUUID());

        var b = svc.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(blocked), UUID.randomUUID());
        assertFalse(b.isOk());
        assertEquals(PlacementResult.Kind.BLOCKED, b.rejection().kind());
        assertTrue(b.rejection().message().toLowerCase().contains("repair"));

        // Andere speler kan wel.
        var ok = svc.create(world, ClaimRectangle.ofCorners(100, 100, 109, 109),
                ClaimOwner.personal(free), UUID.randomUUID());
        assertTrue(ok.isOk());
    }

    // MG-002 — geblokkeerde owner krijgt BLOCKED bij resize.
    @Test
    void blockedOwnerResizeIsRejected() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        var openSvc = serviceWith(ClaimMutationGuard.ALLOW_ALL);
        var world = new WorldRef(UUID.randomUUID());
        var created = openSvc.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertTrue(created.isOk());

        var guard = new ClaimMutationGuard.Builder()
                .block(ClaimType.PERSONAL, owner).build();
        var blockedSvc = serviceWith(guard);
        var r = blockedSvc.resize(created.claim().id(),
                ClaimRectangle.ofCorners(0, 0, 19, 9), UUID.randomUUID());
        assertFalse(r.isOk());
        assertEquals(PlacementResult.Kind.BLOCKED, r.rejection().kind());
    }

    // MG-003 — zonder DB-index weigert de service alsnog een tweede claim.
    @Test
    void serviceRefusesSecondClaimEvenWithoutDatabaseIndex() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        var svc = serviceWith(ClaimMutationGuard.ALLOW_ALL);
        var world = new WorldRef(UUID.randomUUID());
        assertTrue(svc.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID()).isOk());
        // Er is GEEN V6 index in deze test — de defensive check moet toch weigeren.
        var second = svc.create(world, ClaimRectangle.ofCorners(100, 100, 109, 109),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertEquals(PlacementResult.Kind.DUPLICATE_OWNER, second.rejection().kind());
    }

    // MG-004 — ALLOW_ALL blokkeert niets.
    @Test
    void allowAllIsPermissive() {
        UUID p = UUID.randomUUID();
        assertFalse(ClaimMutationGuard.ALLOW_ALL.isBlocked(ClaimType.PERSONAL, p));
        assertFalse(ClaimMutationGuard.ALLOW_ALL.isBlocked(ClaimType.KINGDOM, p));
    }
}
