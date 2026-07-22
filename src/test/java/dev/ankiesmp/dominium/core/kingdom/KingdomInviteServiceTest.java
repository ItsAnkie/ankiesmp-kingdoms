package dev.ankiesmp.dominium.core.kingdom;

import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomInviteServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private KingdomStore store;
    private KingdomService kingdomService;
    private KingdomInviteService inviteService;
    private KingdomVisitorService visitorService;

    private long[] now = { Instant.parse("2026-07-21T12:00:00Z").toEpochMilli() };
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
        @Override public long millis() { return now[0]; }
    };

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("invites.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        store = new SqlKingdomStore(db);
        kingdomService = new KingdomService(store, clock,
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        inviteService = new KingdomInviteService(store, clock, Duration.ofMinutes(10),
                KingdomService.Invalidator.NOOP);
        visitorService = new KingdomVisitorService(store, clock, KingdomService.Invalidator.NOOP);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    // IN-001
    @Test
    void inviteCreatedAndReadable() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        assertTrue(inviteService.invite(leader, target).isOk());
        assertEquals(1, inviteService.invitesFor(target).size());
    }

    // IN-002
    @Test
    void duplicateInviteRejected() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        assertTrue(inviteService.invite(leader, target).isOk());
        var second = inviteService.invite(leader, target);
        assertEquals(KingdomService.Kind.NOT_ALLOWED, second.kind());
    }

    // IN-003
    @Test
    void expiredInviteCannotBeAccepted() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, target);
        now[0] += Duration.ofMinutes(30).toMillis();
        var r = inviteService.accept(target, kid);
        assertFalse(r.isOk());
    }

    // IN-004
    @Test
    void acceptCreatesMembershipAndDeletesInvite() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, target);
        var r = inviteService.accept(target, kid);
        assertTrue(r.isOk());
        assertTrue(store.findInvite(kid, target).isEmpty());
        assertEquals(KingdomRole.MEMBER, store.findMembership(target).orElseThrow().role());
    }

    // IN-005 — accept verwijdert bestaande visitor entry.
    @Test
    void acceptRemovesExistingVisitorEntry() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        visitorService.add(leader, target);
        assertTrue(store.isVisitor(kid, target));
        inviteService.invite(leader, target);
        inviteService.accept(target, kid);
        assertFalse(store.isVisitor(kid, target));
    }

    // IN-006 — target al lid elders.
    @Test
    void cannotAcceptWhenAlreadyElsewhere() {
        UUID leaderA = UUID.randomUUID(), leaderB = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var kidA = kingdomService.create("Alpha", leaderA).value().id();
        var kidB = kingdomService.create("Beta", leaderB).value().id();
        inviteService.invite(leaderA, target);
        inviteService.invite(leaderB, target);
        assertTrue(inviteService.accept(target, kidA).isOk());
        var second = inviteService.accept(target, kidB);
        assertFalse(second.isOk());
    }

    // IN-007
    @Test
    void declineRemovesInvite() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, target);
        assertTrue(inviteService.decline(target, kid).isOk());
        assertTrue(store.findInvite(kid, target).isEmpty());
    }

    // IN-008
    @Test
    void cleanupExpiredRemovesRows() {
        UUID leader = UUID.randomUUID(), target = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        inviteService.invite(leader, target);
        now[0] += Duration.ofMinutes(30).toMillis();
        inviteService.cleanupExpired();
        assertTrue(inviteService.invitesFor(target).isEmpty());
    }

    // IN-009 — self-invite geweigerd.
    @Test
    void selfInviteRejected() {
        UUID leader = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                inviteService.invite(leader, leader).kind());
    }
}
