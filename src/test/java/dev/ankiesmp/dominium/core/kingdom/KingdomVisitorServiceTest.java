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

class KingdomVisitorServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private KingdomStore store;
    private KingdomService kingdomService;
    private KingdomInviteService inviteService;
    private KingdomVisitorService visitorService;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("visitors.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        store = new SqlKingdomStore(db);
        var clock = Clock.systemUTC();
        kingdomService = new KingdomService(store, clock,
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        inviteService = new KingdomInviteService(store, clock, Duration.ofMinutes(10),
                KingdomService.Invalidator.NOOP);
        visitorService = new KingdomVisitorService(store, clock, KingdomService.Invalidator.NOOP);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    // VS-001
    @Test
    void addAndRemove() {
        UUID leader = UUID.randomUUID(), guest = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        assertTrue(visitorService.add(leader, guest).isOk());
        assertTrue(store.isVisitor(kid, guest));
        assertTrue(visitorService.remove(leader, guest).isOk());
        assertFalse(store.isVisitor(kid, guest));
    }

    // VS-002 — bestaande member kan niet visitor worden.
    @Test
    void memberCannotBeAddedAsVisitor() {
        UUID leader = UUID.randomUUID(), member = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        inviteService.invite(leader, member);
        inviteService.accept(member, kid);
        assertEquals(KingdomService.Kind.NOT_ALLOWED,
                visitorService.add(leader, member).kind());
    }

    // VS-003 — visitor die member wordt: entry wordt atomair verwijderd (via acceptInvite).
    @Test
    void visitorEntryRemovedWhenJoining() {
        UUID leader = UUID.randomUUID(), guest = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        visitorService.add(leader, guest);
        assertTrue(store.isVisitor(kid, guest));
        inviteService.invite(leader, guest);
        inviteService.accept(guest, kid);
        assertFalse(store.isVisitor(kid, guest));
    }

    // VS-004 — non-owner mag niet muteren.
    @Test
    void nonMemberCannotManage() {
        UUID leader = UUID.randomUUID(), stranger = UUID.randomUUID(), guest = UUID.randomUUID();
        kingdomService.create("Alpha", leader);
        assertEquals(KingdomService.Kind.NOT_A_MEMBER,
                visitorService.add(stranger, guest).kind());
    }

    // VS-005 — persistentie: nieuwe store-instantie leest de visitor.
    @Test
    void visitorsSurviveRestart() {
        UUID leader = UUID.randomUUID(), guest = UUID.randomUUID();
        var kid = kingdomService.create("Alpha", leader).value().id();
        visitorService.add(leader, guest);
        var fresh = new SqlKingdomStore(db);
        assertTrue(fresh.isVisitor(kid, guest));
    }
}
