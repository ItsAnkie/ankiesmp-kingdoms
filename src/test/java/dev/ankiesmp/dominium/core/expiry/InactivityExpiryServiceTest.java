package dev.ankiesmp.dominium.core.expiry;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimService;
import dev.ankiesmp.dominium.core.claim.PlacementRules;
import dev.ankiesmp.dominium.core.claim.PlacementValidator;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InactivityExpiryServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimIndex index;
    private ClaimService claimService;
    private ClaimBlockLedger ledger;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("expiry.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        ledger = new SqlClaimBlockLedger(db);
        var repo = new SqlClaimRepository(db);
        index = new ClaimIndex();
        var validator = new PlacementValidator(index, PlacementRules.defaults());
        claimService = new ClaimService(index, validator, ledger, new SqlClaimStore(repo));
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private static Clock fixed(Instant when) {
        return Clock.fixed(when, ZoneOffset.UTC);
    }

    private Claim create(UUID owner, int x, int z, Instant createdAt) {
        // grant enough blocks
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(owner))
                .delta(10_000)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID())
                .build());
        var rect = ClaimRectangle.ofCorners(x, z, x + 9, z + 9);
        var r = claimService.create(new WorldRef(UUID.randomUUID()), rect,
                ClaimOwner.personal(owner), UUID.randomUUID());
        assertTrue(r.isOk());
        Claim c = r.claim();
        // Overwrite createdAt via index removal-and-reinsert (Claim is immutable).
        // Voor de test: fabricate direct via new Claim.
        Claim adjusted = new Claim(c.id(), c.world(), c.rect(), c.owner(), createdAt);
        index.remove(c.id());
        index.add(adjusted);
        return adjusted;
    }

    private static PlayerActivityStore stubActivity(UUID id, long lastActive) {
        return new PlayerActivityStore() {
            @Override public void flushBatch(java.util.Map<UUID, ActivityDelta> deltas, long updatedAt) {}
            @Override public Optional<ActivitySnapshot> load(UUID p) {
                if (p.equals(id)) return Optional.of(new ActivitySnapshot(p, lastActive, 0L));
                return Optional.empty();
            }
        };
    }

    // EX-001 — te jonge claim blijft.
    @Test
    void newClaimIsSkipped() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Claim c = create(owner, 0, 0, now.minusSeconds(60));

        var svc = new InactivityExpiryService(index, claimService, stubActivity(owner, 0L),
                fixed(now), id -> false,
                new InactivityExpiryService.Config(true, 1000, 3_600_000L));
        var r = svc.scanAndExpire();
        assertEquals(0, r.expiredCount());
        assertNotNull(index.get(c.id()).orElse(null));
    }

    // EX-002 — offline + inactive owner + oud genoeg → verwijderd + refund.
    @Test
    void offlineInactiveOwnerClaimExpires() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Claim c = create(owner, 0, 0, now.minusSeconds(10_000));
        long balanceBefore = ledger.balanceOrZero(HolderKey.player(owner)).balance();

        var svc = new InactivityExpiryService(index, claimService,
                stubActivity(owner, now.minusSeconds(9_000).toEpochMilli()),
                fixed(now), id -> false,
                new InactivityExpiryService.Config(true, 1000, 1000));
        var r = svc.scanAndExpire();
        assertEquals(1, r.expiredCount());
        assertTrue(index.get(c.id()).isEmpty());
        long balanceAfter = ledger.balanceOrZero(HolderKey.player(owner)).balance();
        assertEquals(balanceBefore + c.rect().cost(), balanceAfter, "refund");
    }

    // EX-003 — online owner wordt niet verwijderd.
    @Test
    void onlineOwnerIsSkipped() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Claim c = create(owner, 0, 0, now.minusSeconds(10_000));
        Set<UUID> online = new HashSet<>(); online.add(owner);
        var svc = new InactivityExpiryService(index, claimService,
                stubActivity(owner, now.minusSeconds(9_000).toEpochMilli()),
                fixed(now), online::contains,
                new InactivityExpiryService.Config(true, 1000, 1000));
        assertEquals(0, svc.scanAndExpire().expiredCount());
        assertNotNull(index.get(c.id()).orElse(null));
    }

    // EX-004 — kingdomclaim wordt nooit door persoonlijke expiry verwijderd.
    @Test
    void kingdomClaimNeverExpired() {
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Claim kingdom = new Claim(UUID.randomUUID(),
                new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(UUID.randomUUID()),
                now.minusSeconds(999_999));
        index.add(kingdom);
        var svc = new InactivityExpiryService(index, claimService,
                stubActivity(UUID.randomUUID(), 0L),
                fixed(now), id -> false,
                new InactivityExpiryService.Config(true, 1, 1));
        assertEquals(0, svc.scanAndExpire().expiredCount());
        assertNotNull(index.get(kingdom.id()).orElse(null));
    }

    // EX-005 — disabled service scant niets.
    @Test
    void disabledConfigDoesNothing() {
        UUID owner = UUID.randomUUID();
        create(owner, 0, 0, Instant.EPOCH);
        var svc = new InactivityExpiryService(index, claimService,
                stubActivity(owner, 0L),
                fixed(Instant.now()), id -> false,
                InactivityExpiryService.Config.disabled());
        assertEquals(0, svc.scanAndExpire().expiredCount());
    }

    // EX-006 — postDeleteHook wordt aangeroepen zodat de cache mee wordt genomen.
    @Test
    void postDeleteHookIsInvokedAfterDelete() {
        UUID owner = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Claim c = create(owner, 0, 0, now.minusSeconds(10_000));

        java.util.List<UUID> invalidated = new java.util.ArrayList<>();
        var svc = new InactivityExpiryService(index, claimService,
                stubActivity(owner, now.minusSeconds(9_000).toEpochMilli()),
                fixed(now), id -> false,
                new InactivityExpiryService.Config(true, 1000, 1000),
                invalidated::add);
        svc.scanAndExpire();
        assertEquals(1, invalidated.size());
        assertEquals(c.id(), invalidated.get(0));
    }
}
