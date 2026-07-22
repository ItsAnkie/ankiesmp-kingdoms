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
 * Integratietests: multi-region via echte ClaimService + SqlClaimRepository.
 * Bewijst dat {@code claim_regions} authoritative is en dat expand alleen
 * unieke nieuwe blocks kost.
 */
class MultiRegionClaimServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimIndex index;
    private ClaimService claimService;
    private ClaimBlockLedger ledger;
    private SqlClaimRepository repo;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("mrs.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        SingleClaimIndexInstaller.install(db);
        ledger = new SqlClaimBlockLedger(db);
        repo = new SqlClaimRepository(db);
        index = new ClaimIndex();
        var validator = new PlacementValidator(index, PlacementRules.defaults());
        claimService = new ClaimService(index, validator, ledger,
                new SqlClaimStore(repo, db, (SqlClaimBlockLedger) ledger));
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void grant(HolderKey h, long amount) {
        ledger.post(PostingRequest.builder()
                .holder(h).delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());
    }

    // MRS-001 — expandGeometry vormt een L-vorm; area = som, bounds correct.
    @Test
    void expandFormsLShape() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertTrue(create.isOk());
        // Voeg L-arm oostelijk toe (10..14, 0..4).
        var exp = claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4), UUID.randomUUID());
        assertEquals(ClaimService.ExpansionResult.Kind.OK, exp.kind());
        assertEquals(25L, exp.extraCost());
        var reloaded = index.get(create.claim().id()).orElseThrow();
        assertEquals(125L, reloaded.geometry().area());
        assertEquals(2, reloaded.geometry().regions().size());
    }

    // MRS-002 — restart: multi-region overleeft via claim_regions.
    @Test
    void multiRegionSurvivesReload() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4), UUID.randomUUID());

        // Verse index uit DB.
        var freshIndex = new ClaimIndex();
        for (var c : repo.loadAll()) freshIndex.add(c);
        var reloaded = freshIndex.get(create.claim().id()).orElseThrow();
        assertEquals(125L, reloaded.geometry().area());
        assertEquals(2, reloaded.geometry().regions().size());
    }

    // MRS-003 — delete refund via unieke area (multi-region).
    @Test
    void deleteRefundsExactUniqueArea() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4), UUID.randomUUID());
        long before = ledger.balanceOrZero(HolderKey.player(owner)).balance();
        var del = claimService.delete(create.claim().id(), UUID.randomUUID());
        long after = ledger.balanceOrZero(HolderKey.player(owner)).balance();
        assertEquals(125L, after - before);
        assertEquals(125L, del.removed().geometry().area());
    }

    // MRS-004 — overlap met eigen geometry: extra cost = alleen unieke blocks.
    @Test
    void overlapWithOwnGeometryCostsOnlyNewBlocks() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        // Selectie 5..14, overlap = 5..9 (5 wide × 10 deep = 50), nieuw = 100-50 = 50
        var exp = claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(5, 0, 14, 9), UUID.randomUUID());
        assertEquals(ClaimService.ExpansionResult.Kind.OK, exp.kind());
        assertEquals(50L, exp.extraCost());
    }

    // MRS-005 — corner-only expansion wordt geweigerd.
    @Test
    void cornerOnlyExpansionRejected() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        var exp = claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(10, 10, 14, 14), UUID.randomUUID());
        assertEquals(ClaimService.ExpansionResult.Kind.REJECTED, exp.kind());
        assertEquals(PlacementResult.Kind.INVALID_GEOMETRY, exp.rejection().kind());
    }

    // MRS-006 — expand met overlap met andere owner wordt geweigerd.
    @Test
    void expandOverlappingOtherOwnerRejected() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        grant(HolderKey.player(a), 10_000);
        grant(HolderKey.player(b), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var ca = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(a), UUID.randomUUID());
        // b's claim aan de oostkant, edge-adjacent aan (0..9,0..9): (10..19,0..9).
        var cb = claimService.create(world, ClaimRectangle.ofCorners(10, 0, 19, 9),
                ClaimOwner.personal(b), UUID.randomUUID());
        // a probeert oost uit te breiden → overlapt met b.
        var exp = claimService.expandGeometry(ca.claim().id(),
                ClaimRectangle.ofCorners(10, 0, 14, 9), UUID.randomUUID());
        assertEquals(ClaimService.ExpansionResult.Kind.REJECTED, exp.kind());
        assertEquals(PlacementResult.Kind.OVERLAP, exp.rejection().kind());
    }

    // MRS-007 — gaten in bounds blijven wilderness: containing() geeft empty.
    @Test
    void gapWithinBoundsIsWilderness() {
        UUID owner = UUID.randomUUID();
        grant(HolderKey.player(owner), 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        // L-vorm: (0..9,0..9) + (10..14,0..4). Punt (12, 8) valt binnen bounds
        // (0..14,0..9) maar niet binnen een region → wilderness.
        var create = claimService.create(world, ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), UUID.randomUUID());
        claimService.expandGeometry(create.claim().id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4), UUID.randomUUID());
        assertTrue(index.containing(world, 5, 5).isPresent(), "in claim");
        assertTrue(index.containing(world, 12, 2).isPresent(), "in expansion arm");
        assertFalse(index.containing(world, 12, 8).isPresent(), "gap → wilderness");
    }
}
