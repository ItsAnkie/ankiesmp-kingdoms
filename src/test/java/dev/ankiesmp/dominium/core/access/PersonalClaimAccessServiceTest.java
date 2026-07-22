package dev.ankiesmp.dominium.core.access;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.storage.access.SqlPersonalClaimAccessStore;
import dev.ankiesmp.dominium.storage.claim.SqlClaimRepository;
import dev.ankiesmp.dominium.storage.claim.SqlClaimStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PersonalClaimAccessServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private PersonalClaimAccessStore store;
    private PersonalClaimAccessService service;
    private RecordingInvalidator invalidator;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("access.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        store = new SqlPersonalClaimAccessStore(db);
        invalidator = new RecordingInvalidator();
        service = new PersonalClaimAccessService(store, Clock.systemUTC(), invalidator);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private Claim insertClaim(UUID ownerId) {
        SqlClaimRepository repo = new SqlClaimRepository(db);
        ClaimIndex index = new ClaimIndex();
        SqlClaimStore claimStore = new SqlClaimStore(repo);
        Claim c = new Claim(UUID.randomUUID(),
                new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(ownerId),
                Instant.now());
        claimStore.insert(c);
        index.add(c);
        return c;
    }

    // AC-001
    @Test
    void trustAddsEntryAndInvalidates() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Claim c = insertClaim(owner);

        var r = service.trust(c, owner, target);
        assertEquals(PersonalClaimAccessService.Kind.OK, r.kind());
        assertEquals(AccessLevel.TRUSTED, store.levelFor(c.id(), target).orElseThrow());
        assertEquals(1, invalidator.accessEvents.size());
        assertEquals(c.id(), invalidator.accessEvents.get(0).claimId);
        assertEquals(target, invalidator.accessEvents.get(0).playerUuid);
    }

    // AC-002
    @Test
    void promoteVisitorToTrustedIsAtomicSingleRow() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Claim c = insertClaim(owner);

        service.visitor(c, owner, target);
        service.trust(c, owner, target);

        assertEquals(AccessLevel.TRUSTED, store.levelFor(c.id(), target).orElseThrow());
        assertEquals(1, countRowsForClaim(c.id()), "promotion mag geen dubbele rijen achterlaten");
    }

    // AC-003
    @Test
    void ownerCannotTrustSelf() {
        UUID owner = UUID.randomUUID();
        Claim c = insertClaim(owner);
        assertEquals(PersonalClaimAccessService.Kind.CANNOT_TARGET_SELF,
                service.trust(c, owner, owner).kind());
        assertTrue(store.listForClaim(c.id()).isEmpty());
    }

    // AC-004
    @Test
    void nonOwnerCannotMutate() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Claim c = insertClaim(owner);
        assertEquals(PersonalClaimAccessService.Kind.NOT_OWNER,
                service.trust(c, stranger, UUID.randomUUID()).kind());
        assertEquals(PersonalClaimAccessService.Kind.NOT_OWNER,
                service.setNoAccess(c, stranger, true).kind());
        assertTrue(store.listForClaim(c.id()).isEmpty());
        assertFalse(store.settingsFor(c.id()).noAccess());
    }

    // AC-005
    @Test
    void untrustNotFoundGivesFeedback() {
        UUID owner = UUID.randomUUID();
        Claim c = insertClaim(owner);
        var r = service.untrust(c, owner, UUID.randomUUID());
        assertEquals(PersonalClaimAccessService.Kind.NOT_FOUND, r.kind());
    }

    // AC-006
    @Test
    void noAccessToggle() {
        UUID owner = UUID.randomUUID();
        Claim c = insertClaim(owner);
        service.setNoAccess(c, owner, true);
        assertTrue(store.settingsFor(c.id()).noAccess());
        service.setNoAccess(c, owner, false);
        assertFalse(store.settingsFor(c.id()).noAccess());
        assertEquals(2, invalidator.settingsEvents.size());
    }

    // AC-007 — restart: nieuwe service-instantie leest bestaande rows.
    @Test
    void entriesSurviveRestart() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Claim c = insertClaim(owner);
        service.trust(c, owner, target);
        service.setNoAccess(c, owner, true);

        var fresh = new PersonalClaimAccessService(store, Clock.systemUTC(),
                PersonalClaimAccessService.Invalidator.NOOP);
        assertEquals(AccessLevel.TRUSTED, store.levelFor(c.id(), target).orElseThrow());
        assertTrue(fresh.settings(c).noAccess());
    }

    // AC-008 — kingdomclaim/adminclaim afgewezen bij trust.
    @Test
    void nonPersonalClaimNotAffectedByOwnerCheck() {
        Claim kingdom = new Claim(UUID.randomUUID(),
                new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(UUID.randomUUID()),
                Instant.now());
        // owner-UUID matcht niet de personal-uuid van deze claim, dus NOT_OWNER.
        assertEquals(PersonalClaimAccessService.Kind.NOT_OWNER,
                service.trust(kingdom, UUID.randomUUID(), UUID.randomUUID()).kind());
    }

    private long countRowsForClaim(UUID claimId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM personal_claim_access WHERE claim_id = ?")) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    private static final class RecordingInvalidator implements PersonalClaimAccessService.Invalidator {
        record Event(UUID claimId, UUID playerUuid) {}
        final List<Event> accessEvents = new CopyOnWriteArrayList<>();
        final List<UUID> settingsEvents = new CopyOnWriteArrayList<>();
        @Override public void onAccessChanged(UUID c, UUID p) { accessEvents.add(new Event(c, p)); }
        @Override public void onSettingsChanged(UUID c) { settingsEvents.add(c); }
    }
}
