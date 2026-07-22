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
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test voor de duplicate-repair flow tegen een échte SQLite-DB:
 * seed twee claims voor dezelfde owner, run de installer (DEFERRED), block
 * de owner, doe een repair-delete, verifieer dat V6 daarna kan worden geplaatst
 * en dat de guard leeg is.
 */
class ClaimRepairIntegrationTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimBlockLedger ledger;
    private ClaimIndex index;
    private ClaimService claimService;
    private SqlClaimRepository repo;
    private MutableClaimMutationGuard guard;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("repair.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        ledger = new SqlClaimBlockLedger(db);
        repo = new SqlClaimRepository(db);
        index = new ClaimIndex();
        var validator = new PlacementValidator(index, PlacementRules.defaults());
        guard = new MutableClaimMutationGuard();
        claimService = new ClaimService(index, validator, ledger,
                new SqlClaimStore(repo), guard);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void seedRawClaim(UUID id, UUID owner, WorldRef world,
                              int minX, int minZ, int maxX, int maxZ) {
        // Bypass ClaimService om de duplicate direct in de DB te zetten
        // (simuleert een DB uit oudere builds).
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO claims(id, world_id, owner_type, owner_id, min_x, min_z, max_x, max_z, created_at) " +
                            "VALUES (?, ?, 'PERSONAL', ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, world.id().toString());
                ps.setString(3, owner.toString());
                ps.setInt(4, minX);
                ps.setInt(5, minZ);
                ps.setInt(6, maxX);
                ps.setInt(7, maxZ);
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
        index.add(new Claim(id, world,
                ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ),
                ClaimOwner.personal(owner), Instant.now()));
    }

    // RI-001 — bij duplicaten: installer=DEFERRED en owner geblokkeerd via guard.
    @Test
    void deferredInstallerBlocksOwnerNotOthers() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        WorldRef world = new WorldRef(UUID.randomUUID());
        seedRawClaim(UUID.randomUUID(), owner, world, 0, 0, 9, 9);
        seedRawClaim(UUID.randomUUID(), owner, world, 100, 100, 109, 109);

        var installer = SingleClaimIndexInstaller.install(db);
        assertEquals(SingleClaimIndexInstaller.Kind.DEFERRED, installer.kind());
        for (var c : installer.conflicts()) guard.block(c.type(), c.ownerId());

        assertTrue(guard.isBlocked(ClaimType.PERSONAL, owner));
        // Andere speler kan gewoon een claim maken (mits saldo).
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(other)).delta(10_000)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());
        var r = claimService.create(world,
                ClaimRectangle.ofCorners(200, 200, 209, 209),
                ClaimOwner.personal(other), UUID.randomUUID());
        assertTrue(r.isOk(), "andere owner blijft ongeblokkeerd");
    }

    // RI-002 — na repair: guard leeg, V6 kan worden geplaatst.
    @Test
    void afterRepairGuardIsClearedAndV6Applies() {
        UUID owner = UUID.randomUUID();
        WorldRef world = new WorldRef(UUID.randomUUID());
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        seedRawClaim(c1, owner, world, 0, 0, 9, 9);
        seedRawClaim(c2, owner, world, 100, 100, 109, 109);

        var installer = SingleClaimIndexInstaller.install(db);
        for (var c : installer.conflicts()) guard.block(c.type(), c.ownerId());
        assertTrue(guard.isBlocked(ClaimType.PERSONAL, owner));

        // Admin verwijdert één claim via de bestaande claim-service.
        claimService.delete(c2, UUID.randomUUID());
        // Na repair: nog maar 1 claim voor deze owner → unblock.
        long remaining = index.all().stream()
                .filter(c -> c.owner().type() == ClaimType.PERSONAL
                        && c.owner().id().equals(owner)).count();
        assertEquals(1, remaining);
        guard.unblock(ClaimType.PERSONAL, owner);
        assertFalse(guard.isBlocked(ClaimType.PERSONAL, owner));

        // Volgende install slaagt.
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED,
                SingleClaimIndexInstaller.install(db).kind());

        // Andere claim voor dezelfde owner wordt nu direct geweigerd door de service (defensive).
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(owner)).delta(10_000)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());
        var second = claimService.create(world,
                ClaimRectangle.ofCorners(500, 500, 509, 509),
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertEquals(PlacementResult.Kind.DUPLICATE_OWNER, second.rejection().kind());
    }

    // RI-003 — safe merge is atomair genoeg: delete extra + resize keep, guard vrijgegeven.
    @Test
    void safeMergeFlow() {
        UUID owner = UUID.randomUUID();
        WorldRef world = new WorldRef(UUID.randomUUID());
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        seedRawClaim(c1, owner, world, 0, 0, 9, 9);
        seedRawClaim(c2, owner, world, 10, 0, 19, 9);

        var installer = SingleClaimIndexInstaller.install(db);
        for (var c : installer.conflicts()) guard.block(c.type(), c.ownerId());

        long balance = 1000;
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(owner)).delta(balance)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());

        var claims = List.of(index.get(c1).orElseThrow(), index.get(c2).orElseThrow());
        var plan = ClaimRepairPlan.analyse(claims, balance, index);
        assertEquals(ClaimRepairPlan.Kind.SAFE_MERGE, plan.kind());
        assertEquals(0, plan.extraCost());

        // Voer merge uit (admin-pad): tijdelijk unblock, delete extras, resize keep.
        guard.unblock(ClaimType.PERSONAL, owner);
        claimService.delete(c2, UUID.randomUUID());
        var resized = claimService.resize(c1, plan.union(), UUID.randomUUID());
        assertTrue(resized.isOk());

        // V6 slaagt.
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED,
                SingleClaimIndexInstaller.install(db).kind());
        // Nog exact één claim over.
        assertEquals(1, index.all().stream()
                .filter(c -> c.owner().type() == ClaimType.PERSONAL
                        && c.owner().id().equals(owner)).count());
    }
}
