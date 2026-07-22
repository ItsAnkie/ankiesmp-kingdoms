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
import dev.ankiesmp.dominium.storage.migrations.SingleClaimIndexInstaller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifieert de "één claim per owner"-invariant. De service-laag weigert
 * een tweede claim voor dezelfde owner ONGEACHT of de V6 unique index
 * al geïnstalleerd is — dat is bewust, zodat repair-mode-databases
 * evenmin een tweede claim krijgen.
 */
class SingleClaimPerOwnerTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimIndex index;
    private ClaimService claimService;
    private ClaimBlockLedger ledger;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("one-claim.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED,
                SingleClaimIndexInstaller.install(db).kind());
        ledger = new SqlClaimBlockLedger(db);
        var repo = new SqlClaimRepository(db);
        index = new ClaimIndex();
        var validator = new PlacementValidator(index, PlacementRules.defaults());
        claimService = new ClaimService(index, validator, ledger, new SqlClaimStore(repo));
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void grant(HolderKey holder, long amount) {
        ledger.post(PostingRequest.builder()
                .holder(holder).delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID())
                .build());
    }

    // SC-001 — service weigert tweede PERSONAL-claim met DUPLICATE_OWNER.
    @Test
    void serviceRejectsSecondPersonalClaim() {
        UUID owner = UUID.randomUUID();
        HolderKey h = HolderKey.player(owner);
        grant(h, 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var first = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertTrue(first.isOk());
        var second = claimService.create(world, ClaimRectangle.ofCorners(100, 100, 109, 109),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertFalse(second.isOk());
        assertEquals(PlacementResult.Kind.DUPLICATE_OWNER, second.rejection().kind());
    }

    // SC-002 — service weigert tweede KINGDOM-claim.
    @Test
    void serviceRejectsSecondKingdomClaim() {
        UUID kingdomId = UUID.randomUUID();
        HolderKey h = HolderKey.kingdom(kingdomId);
        grant(h, 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        assertTrue(claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(kingdomId), UUID.randomUUID()).isOk());
        var second = claimService.create(world, ClaimRectangle.ofCorners(100, 100, 109, 109),
                ClaimOwner.kingdom(kingdomId), UUID.randomUUID());
        assertEquals(PlacementResult.Kind.DUPLICATE_OWNER, second.rejection().kind());
    }

    // SC-003 — findByOwner vindt de bestaande claim.
    @Test
    void findByOwnerReturnsExisting() {
        UUID owner = UUID.randomUUID();
        HolderKey h = HolderKey.player(owner);
        grant(h, 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var r = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertTrue(index.findByOwner(ClaimType.PERSONAL, owner).isPresent());
        assertEquals(r.claim().id(),
                index.findByOwner(ClaimType.PERSONAL, owner).orElseThrow().id());
    }

    // SC-004 — audit vindt geen duplicaten in een schone DB.
    @Test
    void auditFindsNoConflicts() {
        assertTrue(DuplicateOwnerAudit.scan(index).isEmpty());
    }
}
