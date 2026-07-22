package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimServiceTest {

    @TempDir Path tempDir;

    private Database db;
    private ClaimBlockLedger ledger;
    private ClaimIndex index;
    private ClaimService service;
    private InMemoryStore store;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("cs.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        ledger = new SqlClaimBlockLedger(db);
        index = new ClaimIndex();
        store = new InMemoryStore();
        service = new ClaimService(index,
                new PlacementValidator(index, PlacementRules.defaults()),
                ledger, store);
    }

    @AfterEach
    void tearDown() { db.close(); }

    @Test
    void createSpendsExactAreaFromLedger() {
        UUID player = UUID.randomUUID();
        grant(player, 500);

        var rect = new ClaimRectangle(0, 0, 9, 9); // 100 blocks
        var result = service.create(new WorldRef(UUID.randomUUID()),
                rect, ClaimOwner.personal(player), UUID.randomUUID());

        assertTrue(result.isOk(), () -> "expected ok, got " + result.rejection());
        assertEquals(400L, ledger.balanceOrZero(HolderKey.player(player)).balance());
        assertEquals(1, store.claims.size());
        assertTrue(index.get(result.claim().id()).isPresent());
    }

    @Test
    void createRejectsWhenBalanceInsufficient() {
        UUID player = UUID.randomUUID();
        grant(player, 50); // needs 100

        var rect = new ClaimRectangle(0, 0, 9, 9);
        var result = service.create(new WorldRef(UUID.randomUUID()),
                rect, ClaimOwner.personal(player), UUID.randomUUID());

        assertFalse(result.isOk());
        assertEquals(PlacementResult.Kind.INSUFFICIENT_CLAIM_BLOCKS, result.rejection().kind());
        assertEquals(50L, ledger.balanceOrZero(HolderKey.player(player)).balance());
        assertTrue(store.claims.isEmpty());
    }

    @Test
    void resizeExpansionBooksOnlyDelta() {
        UUID player = UUID.randomUUID();
        grant(player, 500);
        var world = new WorldRef(UUID.randomUUID());

        var initial = service.create(world, new ClaimRectangle(0, 0, 9, 9),
                ClaimOwner.personal(player), UUID.randomUUID());
        assertTrue(initial.isOk());
        long afterCreate = ledger.balanceOrZero(HolderKey.player(player)).balance();
        assertEquals(400L, afterCreate);

        var resized = service.resize(initial.claim().id(),
                new ClaimRectangle(0, 0, 14, 9), // 15x10 = 150 (delta +50)
                UUID.randomUUID());
        assertTrue(resized.isOk());
        assertEquals(50L, resized.claimBlockDelta());
        assertEquals(350L, ledger.balanceOrZero(HolderKey.player(player)).balance());
    }

    @Test
    void resizeShrinkRefundsDifference() {
        UUID player = UUID.randomUUID();
        grant(player, 500);
        var world = new WorldRef(UUID.randomUUID());

        var initial = service.create(world, new ClaimRectangle(0, 0, 14, 9),
                ClaimOwner.personal(player), UUID.randomUUID());
        assertTrue(initial.isOk());
        assertEquals(350L, ledger.balanceOrZero(HolderKey.player(player)).balance());

        var resized = service.resize(initial.claim().id(),
                new ClaimRectangle(0, 0, 9, 9), UUID.randomUUID()); // -50
        assertTrue(resized.isOk());
        assertEquals(-50L, resized.claimBlockDelta());
        assertEquals(400L, ledger.balanceOrZero(HolderKey.player(player)).balance());
    }

    @Test
    void deleteRefundsFullArea() {
        UUID player = UUID.randomUUID();
        grant(player, 500);
        var world = new WorldRef(UUID.randomUUID());

        var claim = service.create(world, new ClaimRectangle(0, 0, 9, 9),
                ClaimOwner.personal(player), UUID.randomUUID()).claim();
        assertEquals(400L, ledger.balanceOrZero(HolderKey.player(player)).balance());

        var deleted = service.delete(claim.id(), UUID.randomUUID());
        assertEquals(claim.id(), deleted.removed().id());
        assertEquals(500L, ledger.balanceOrZero(HolderKey.player(player)).balance());
        assertTrue(index.get(claim.id()).isEmpty());
    }

    private void grant(UUID player, long amount) {
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(player))
                .delta(amount)
                .reason(ClaimBlockReason.INITIAL_GRANT)
                .idempotencyKey(UUID.randomUUID())
                .build());
    }

    private static final class InMemoryStore implements ClaimService.ClaimStore {
        final List<Claim> claims = new ArrayList<>();
        @Override public void insert(Claim claim) { claims.add(claim); }
        @Override public void updateRectangle(UUID claimId, ClaimRectangle oldRect, ClaimRectangle newRect) {
            for (int i = 0; i < claims.size(); i++) {
                if (claims.get(i).id().equals(claimId)) {
                    claims.set(i, claims.get(i).withRect(newRect));
                    return;
                }
            }
            throw new IllegalStateException("unknown " + claimId);
        }
        @Override public void delete(UUID claimId) { claims.removeIf(c -> c.id().equals(claimId)); }
    }
}
