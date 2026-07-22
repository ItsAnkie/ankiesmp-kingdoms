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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomMembershipServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private KingdomStore store;
    private KingdomService kingdomService;
    private KingdomMembershipService membershipService;
    private KingdomInviteService inviteService;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("membership.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        store = new SqlKingdomStore(db);
        var clock = Clock.systemUTC();
        kingdomService = new KingdomService(store, clock,
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        membershipService = new KingdomMembershipService(store, clock, KingdomService.Invalidator.NOOP);
        inviteService = new KingdomInviteService(store, clock, Duration.ofMinutes(10),
                KingdomService.Invalidator.NOOP);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private UUID makeKingdomWithMembers(UUID leader, UUID... members) {
        var kid = kingdomService.create("Alpha", leader).value().id();
        for (UUID m : members) {
            inviteService.invite(leader, m);
            inviteService.accept(m, kid);
        }
        return kid;
    }

    // MB-001
    @Test
    void promoteMemberToCoLeader() {
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, member);
        var r = membershipService.promote(leader, member);
        assertTrue(r.isOk());
        assertEquals(KingdomRole.CO_LEADER, store.findMembership(member).orElseThrow().role());
    }

    // MB-002
    @Test
    void coLeaderCannotPromote() {
        UUID leader = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, a, b);
        membershipService.promote(leader, a);
        var r = membershipService.promote(a, b);
        assertEquals(KingdomService.Kind.NOT_ALLOWED, r.kind());
    }

    // MB-003
    @Test
    void coLeaderCannotKickAnotherCoLeader() {
        UUID leader = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, a, b);
        membershipService.promote(leader, a);
        membershipService.promote(leader, b);
        var r = membershipService.kick(a, b);
        assertEquals(KingdomService.Kind.NOT_ALLOWED, r.kind());
    }

    // MB-004
    @Test
    void coLeaderCanKickRegularMember() {
        UUID leader = UUID.randomUUID(), co = UUID.randomUUID(), reg = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, co, reg);
        membershipService.promote(leader, co);
        var r = membershipService.kick(co, reg);
        assertTrue(r.isOk());
        assertTrue(store.findMembership(reg).isEmpty());
    }

    // MB-005
    @Test
    void demoteOnlyWorksOnCoLeader() {
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, member);
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.demote(leader, member).kind(),
                "member is niet CO_LEADER, dus demote weigert");
        membershipService.promote(leader, member);
        assertTrue(membershipService.demote(leader, member).isOk());
        assertEquals(KingdomRole.MEMBER, store.findMembership(member).orElseThrow().role());
    }

    // MB-006 — leader kan niet worden gekickt/gedemoted door wie dan ook.
    @Test
    void leaderCannotBeKickedOrDemoted() {
        UUID leader = UUID.randomUUID(), co = UUID.randomUUID();
        var kid = makeKingdomWithMembers(leader, co);
        membershipService.promote(leader, co);
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.kick(co, leader).kind());
        // Demote van leader is niet gedefinieerd — leader is niet CO_LEADER, dus NOT_ALLOWED.
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.demote(co, leader).kind());
    }

    // MB-007 — self-target regels.
    @Test
    void selfTargetOperationsRejected() {
        UUID leader = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.kick(leader, leader).kind());
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.promote(leader, leader).kind());
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.demote(leader, leader).kind());
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                membershipService.transferLeadership(leader, leader).kind());
    }
}
