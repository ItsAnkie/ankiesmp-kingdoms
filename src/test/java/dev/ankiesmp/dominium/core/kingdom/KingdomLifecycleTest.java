package dev.ankiesmp.dominium.core.kingdom;

import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomLifecycleTest {

    @TempDir Path tempDir;
    private Database db;
    private KingdomStore store;
    private KingdomService kingdomService;
    private KingdomMembershipService membershipService;
    private KingdomInviteService inviteService;
    private KingdomVisitorService visitorService;

    private long[] clockNow = { Instant.parse("2026-07-21T12:00:00Z").toEpochMilli() };

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("kingdoms.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        store = new SqlKingdomStore(db);
        Clock clock = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId z) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(clockNow[0]); }
            @Override public long millis() { return clockNow[0]; }
        };
        var invalidator = KingdomService.Invalidator.NOOP;
        kingdomService = new KingdomService(store, clock,
                new KingdomService.NameConfig(3, 24), invalidator);
        membershipService = new KingdomMembershipService(store, clock, invalidator);
        inviteService = new KingdomInviteService(store, clock, Duration.ofMinutes(10), invalidator);
        visitorService = new KingdomVisitorService(store, clock, invalidator);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    // KL-001
    @Test
    void createProducesLeaderMembership() {
        UUID leader = UUID.randomUUID();
        var r = kingdomService.create("Roseborough", leader);
        assertTrue(r.isOk());
        var member = store.findMembership(leader).orElseThrow();
        assertEquals(KingdomRole.LEADER, member.role());
        assertEquals(1, countMembers(r.value().id()));
    }

    // KL-002
    @Test
    void duplicateNameCaseInsensitiveRejected() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(kingdomService.create("Roseborough", a).isOk());
        var r = kingdomService.create("ROSEBOROUGH", b);
        assertEquals(KingdomService.Kind.NAME_TAKEN, r.kind());
    }

    // KL-003
    @Test
    void playerCannotBeInTwoKingdoms() {
        UUID p = UUID.randomUUID();
        assertTrue(kingdomService.create("Alpha", p).isOk());
        var r = kingdomService.create("Beta", p);
        assertEquals(KingdomService.Kind.ALREADY_IN_KINGDOM, r.kind());
    }

    // KL-004
    @Test
    void leaderCannotLeave() {
        UUID leader = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        var r = membershipService.leave(leader);
        assertEquals(KingdomService.Kind.NOT_ALLOWED, r.kind());
    }

    // KL-005
    @Test
    void memberCanLeave() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, member);
        inviteService.accept(member, kid);
        var r = membershipService.leave(member);
        assertTrue(r.isOk());
        assertTrue(store.findMembership(member).isEmpty());
    }

    // KL-006
    @Test
    void disbandRemovesRelatedData() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, member);
        inviteService.accept(member, kid);
        visitorService.add(leader, visitor);

        var r = kingdomService.disband(kid, leader);
        assertTrue(r.isOk());
        assertTrue(kingdomService.findById(kid).isEmpty());
        assertTrue(store.findMembership(leader).isEmpty());
        assertTrue(store.findMembership(member).isEmpty());
        assertTrue(store.listVisitors(kid).isEmpty());
    }

    // KL-007
    @Test
    void leadershipTransferIsAtomic() {
        UUID leader = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, target);
        inviteService.accept(target, kid);

        var r = membershipService.transferLeadership(leader, target);
        assertTrue(r.isOk());
        assertEquals(KingdomRole.LEADER, store.findMembership(target).orElseThrow().role());
        assertEquals(KingdomRole.MEMBER, store.findMembership(leader).orElseThrow().role());
        assertEquals(1, countLeaders(kid));
    }

    // KL-008
    @Test
    void failedTransferOnUnknownTargetLeavesLeaderIntact() {
        UUID leader = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        var r = membershipService.transferLeadership(leader, UUID.randomUUID());
        assertFalse(r.isOk());
        assertEquals(KingdomRole.LEADER, store.findMembership(leader).orElseThrow().role());
    }

    // KL-009
    @Test
    void exactlyOneLeaderInvariantHolds() {
        UUID leader = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        assertEquals(1, countLeaders(kid));

        UUID p = UUID.randomUUID();
        inviteService.invite(leader, p);
        inviteService.accept(p, kid);
        assertEquals(1, countLeaders(kid));

        membershipService.promote(leader, p);
        assertEquals(1, countLeaders(kid));

        membershipService.transferLeadership(leader, p);
        assertEquals(1, countLeaders(kid));
    }

    // KL-010 — invalid name.
    @Test
    void invalidNamesRejected() {
        UUID leader = UUID.randomUUID();
        assertEquals(KingdomService.Kind.INVALID_NAME,
                kingdomService.create("!!", leader).kind());
        assertEquals(KingdomService.Kind.INVALID_NAME,
                kingdomService.create("A", leader).kind());
        assertEquals(KingdomService.Kind.INVALID_NAME,
                kingdomService.create("bad$char", leader).kind());
    }

    private long countLeaders(UUID kingdomId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM kingdom_members WHERE kingdom_id = ? AND role = 'LEADER'")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    private long countMembers(UUID kingdomId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM kingdom_members WHERE kingdom_id = ?")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    List<KingdomMember> membersOf(UUID id) { return store.listMembers(id); }
}
