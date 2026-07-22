package dev.ankiesmp.dominium.storage.claim;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.integrations.WorldGuardHook;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure-injection tests: gooit bij iedere mutatiestap in
 * {@link AtomicClaimOps#expandAtomic} en {@link AtomicClaimOps#mergeAtomic}
 * een {@link SQLException} en bewijst dat de héle transactie terugrolt
 * (geen ledger-post blijft staan, geen claim/regions-mutatie zichtbaar).
 */
class AtomicClaimOpsFailureTest {

    @TempDir Path tempDir;
    private Database db;
    private SqlClaimRepository repo;
    private SqlClaimBlockLedger ledger;
    private AtomicClaimOps ops;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("acofit.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        // Bewust GEEN SingleClaimIndexInstaller: merge-tests seeden 2 claims per
        // owner om duplicate-repair na te bootsen — dat kan alleen zonder de
        // V6 unique-index.
        ledger = new SqlClaimBlockLedger(db);
        repo = new SqlClaimRepository(db);
        ops = new AtomicClaimOps(db, repo, ledger);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void grant(HolderKey h, long amount) {
        ledger.post(PostingRequest.builder()
                .holder(h).delta(amount)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());
    }

    private Claim seedClaim(HolderKey holder, WorldRef world, ClaimRectangle rect) {
        UUID id = UUID.randomUUID();
        Claim c = new Claim(id, world,
                ClaimGeometry.ofRectangle(rect),
                ClaimOwner.personal(holder.id()),
                Instant.now());
        repo.insert(c);
        return c;
    }

    private static AtomicClaimOps.Hook throwAt(int step) {
        AtomicInteger n = new AtomicInteger();
        return new AtomicClaimOps.Hook() {
            @Override public void afterLedgerSpend() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@afterLedgerSpend");
            }
            @Override public void afterRegionsDelete() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@afterRegionsDelete");
            }
            @Override public void afterFirstRegionInsert() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@afterFirstRegionInsert");
            }
            @Override public void afterBoundsUpdate() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@afterBoundsUpdate");
            }
            @Override public void afterRevisionWrite() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@afterRevisionWrite");
            }
            @Override public void beforeCommit() throws SQLException {
                if (n.incrementAndGet() == step) throw new SQLException("inject@beforeCommit");
            }
        };
    }

    // ACO-E1..E6 — expandAtomic: injecteer een fout bij elke stap; verwacht rollback
    // van claim (regions ongewijzigd) én ledger (balance onveranderd).
    @Test
    void expandAtomicRollsBackOnAnyStepFailure() {
        for (int step = 1; step <= 6; step++) {
            UUID ownerId = UUID.randomUUID();
            HolderKey owner = HolderKey.player(ownerId);
            grant(owner, 10_000);
            WorldRef world = new WorldRef(UUID.randomUUID());
            var seed = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
            long balanceBefore = ledger.balanceOrZero(owner).balance();
            long regionsBefore = countRegions(seed.id());

            int s = step;
            assertThrows(RuntimeException.class,
                    () -> ops.expandAtomic(seed.id(),
                            ClaimRectangle.ofCorners(10, 0, 14, 4),
                            UUID.randomUUID(),
                            WorldGuardHook.NO_OP, true,
                            throwAt(s)),
                    "step " + step + ": injected SQLException must surface");

            assertEquals(balanceBefore, ledger.balanceOrZero(owner).balance(),
                    "step " + step + ": ledger must NOT be debited");
            assertEquals(regionsBefore, countRegions(seed.id()),
                    "step " + step + ": regions must be unchanged");
        }
    }

    // ACO-E-OK — expandAtomic without failure injection succeeds and persists.
    @Test
    void expandAtomicSuccessCommits() {
        UUID ownerId = UUID.randomUUID();
        HolderKey owner = HolderKey.player(ownerId);
        grant(owner, 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var seed = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));

        var res = ops.expandAtomic(seed.id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4),
                UUID.randomUUID(),
                WorldGuardHook.NO_OP, true,
                AtomicClaimOps.NO_HOOK);
        assertTrue(res.ok(), () -> "expected ok, got " + res.rejection());
        assertEquals(25L, res.extraCost());
        assertEquals(10_000L - 25L, ledger.balanceOrZero(owner).balance());
        assertEquals(2, countRegions(seed.id()));
    }

    // ACO-E-WG — WG conflict rolls back everything.
    @Test
    void expandAtomicWorldGuardConflictRollsBack() {
        UUID ownerId = UUID.randomUUID();
        HolderKey owner = HolderKey.player(ownerId);
        grant(owner, 10_000);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var seed = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
        long balanceBefore = ledger.balanceOrZero(owner).balance();
        long regionsBefore = countRegions(seed.id());

        WorldGuardHook blocking = new WorldGuardHook() {
            @Override public boolean available() { return true; }
            @Override public java.util.Optional<String> firstBlockingRegion(
                    WorldRef w, List<ClaimRectangle> p, boolean ig) {
                return java.util.Optional.of("spawn");
            }
        };
        var res = ops.expandAtomic(seed.id(),
                ClaimRectangle.ofCorners(10, 0, 14, 4),
                UUID.randomUUID(),
                blocking, true,
                AtomicClaimOps.NO_HOOK);
        assertFalse(res.ok());
        assertTrue(res.rejection().contains("worldguard"));
        assertEquals(balanceBefore, ledger.balanceOrZero(owner).balance());
        assertEquals(regionsBefore, countRegions(seed.id()));
    }

    // ACO-M1..M5 — mergeAtomic: identical duplicates, inject fail at each mutation
    // step (regions delete/insert/bounds/revision/beforeCommit). Verify everything
    // rolls back: no ledger refund posted, no claim deleted, no geometry change.
    @Test
    void mergeAtomicRollsBackOnAnyStepFailure() {
        for (int step = 1; step <= 5; step++) {
            UUID ownerId = UUID.randomUUID();
            HolderKey owner = HolderKey.player(ownerId);
            WorldRef world = new WorldRef(UUID.randomUUID());
            var a = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
            var b = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
            long balanceBefore = ledger.balanceOrZero(owner).balance();

            int s = step;
            assertThrows(RuntimeException.class,
                    () -> ops.mergeAtomic(a.owner().type(), ownerId,
                            WorldGuardHook.NO_OP, true, throwAt(s)),
                    "step " + step + ": injected SQLException must surface");

            assertEquals(balanceBefore, ledger.balanceOrZero(owner).balance(),
                    "step " + step + ": ledger unchanged after rollback");
            assertTrue(repo.findById(a.id()).isPresent(),
                    "step " + step + ": keep-claim must still exist");
            assertTrue(repo.findById(b.id()).isPresent(),
                    "step " + step + ": deleted-claim must still exist (rollback)");
        }
    }

    // ACO-M-OK — mergeAtomic on two identical duplicate claims → refund + one remains.
    @Test
    void mergeAtomicDuplicatesSucceedsWithRefund() {
        UUID ownerId = UUID.randomUUID();
        HolderKey owner = HolderKey.player(ownerId);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var a = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
        var b = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));

        long balanceBefore = ledger.balanceOrZero(owner).balance();
        var res = ops.mergeAtomic(a.owner().type(), ownerId,
                WorldGuardHook.NO_OP, true, AtomicClaimOps.NO_HOOK);
        assertTrue(res.ok(), () -> "expected ok, got: " + res.rejection());
        assertEquals(100L, res.refund(), "overlap refund = area of one identical rect");
        assertEquals(balanceBefore + 100L, ledger.balanceOrZero(owner).balance());
        // Exact één claim over.
        boolean keptA = repo.findById(a.id()).isPresent();
        boolean keptB = repo.findById(b.id()).isPresent();
        assertTrue(keptA ^ keptB, "exactly one of the two duplicates must remain");
    }

    // ACO-M-WG — WG conflict on bbox rolls back merge.
    @Test
    void mergeAtomicWorldGuardConflictRollsBack() {
        UUID ownerId = UUID.randomUUID();
        HolderKey owner = HolderKey.player(ownerId);
        WorldRef world = new WorldRef(UUID.randomUUID());
        var a = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
        var b = seedClaim(owner, world, ClaimRectangle.ofCorners(0, 0, 9, 9));
        long balanceBefore = ledger.balanceOrZero(owner).balance();

        WorldGuardHook blocking = new WorldGuardHook() {
            @Override public boolean available() { return true; }
            @Override public java.util.Optional<String> firstBlockingRegion(
                    WorldRef w, List<ClaimRectangle> p, boolean ig) {
                return java.util.Optional.of("spawn");
            }
        };
        var res = ops.mergeAtomic(a.owner().type(), ownerId, blocking, true, AtomicClaimOps.NO_HOOK);
        assertFalse(res.ok());
        assertTrue(res.rejection().contains("worldguard"));
        assertEquals(balanceBefore, ledger.balanceOrZero(owner).balance());
        assertTrue(repo.findById(a.id()).isPresent() && repo.findById(b.id()).isPresent());
    }

    private long countRegions(UUID claimId) {
        return db.withConnection(conn -> {
            try (var ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_regions WHERE claim_id = ?")) {
                ps.setString(1, claimId.toString());
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }
}
