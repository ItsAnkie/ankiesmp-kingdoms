package dev.ankiesmp.dominium.core.earning;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockAdminOps;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.earning.SqlEarningStore;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActivePlayEarnerTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimBlockLedger ledger;
    private EarningStore earnStore;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("earn.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        ledger = new SqlClaimBlockLedger(db);
        earnStore = new SqlEarningStore(db);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private ActivePlayEarner earner(long perInterval, long dailyCap) {
        return new ActivePlayEarner(ledger, earnStore,
                new ActivePlayEarner.EarningConfig(perInterval, 300, dailyCap));
    }

    // EA-001
    @Test
    void awardsPositiveDeltaOnLedger() {
        UUID p = UUID.randomUUID();
        var e = earner(4, 500);
        var o = e.award(p, Instant.parse("2026-07-21T12:00:00Z"), 100);
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, o.kind());
        assertEquals(4, o.grantedBlocks());
        assertEquals(4, ledger.balanceOrZero(HolderKey.player(p)).balance());
    }

    // EA-002 — dubbele call op zelfde slot boekt niet dubbel (idempotent op ledger).
    @Test
    void duplicateSlotIsIdempotent() {
        UUID p = UUID.randomUUID();
        var e = earner(4, 500);
        var t = Instant.parse("2026-07-21T12:00:00Z");
        e.award(p, t, 100);
        var second = e.award(p, t, 100);
        // Belangrijk: de store reserveert nogmaals 4, maar ledger.post ziet dubbele
        // idempotency-key en retourneert ALREADY_APPLIED — dus totaalbalans blijft 4.
        assertEquals(ActivePlayEarner.Outcome.Kind.DUPLICATE, second.kind());
        assertEquals(4, ledger.balanceOrZero(HolderKey.player(p)).balance());
    }

    // EA-003 — daily cap atomair afgedwongen.
    @Test
    void dailyCapCapsAcrossManySlots() {
        UUID p = UUID.randomUUID();
        var e = earner(10, 25);
        var t = Instant.parse("2026-07-21T12:00:00Z");
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, e.award(p, t, 1).kind());
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, e.award(p, t.plusSeconds(300), 2).kind());
        // Derde interval zou 30 zijn, cap is 25 → 5 blocks toegekend.
        var third = e.award(p, t.plusSeconds(600), 3);
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, third.kind());
        assertEquals(5, third.grantedBlocks());
        // Vierde: 30 al bereikt (10+10+5=25) — CAPPED.
        var fourth = e.award(p, t.plusSeconds(900), 4);
        assertEquals(ActivePlayEarner.Outcome.Kind.CAPPED, fourth.kind());
        assertEquals(25, ledger.balanceOrZero(HolderKey.player(p)).balance());
        assertEquals(25, e.earnedToday(p, t));
        assertEquals(0, e.dailyCapRemaining(p, t));
    }

    // EA-004 — UTC-dagwisseling reset de cap.
    @Test
    void utcDayBoundaryResetsCap() {
        UUID p = UUID.randomUUID();
        var e = earner(10, 10);
        Instant day1 = LocalDate.of(2026, 7, 21)
                .atTime(23, 59, 30).toInstant(ZoneOffset.UTC);
        Instant day2 = LocalDate.of(2026, 7, 22)
                .atTime(0, 0, 30).toInstant(ZoneOffset.UTC);
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, e.award(p, day1, 1).kind());
        assertEquals(ActivePlayEarner.Outcome.Kind.CAPPED,  e.award(p, day1, 2).kind());
        assertEquals(ActivePlayEarner.Outcome.Kind.AWARDED, e.award(p, day2, 3).kind());
        assertEquals(20, ledger.balanceOrZero(HolderKey.player(p)).balance());
    }

    // EA-005 — starting-balance=0 (elders geregeld) mag earning niet uitschakelen.
    //  Deze test dekt dat een earner met blocksPerInterval=0 uit is.
    @Test
    void zeroConfigDisablesEarning() {
        var e = earner(0, 500);
        assertFalse(e.enabled());
        var out = e.award(UUID.randomUUID(), Instant.now(), 1);
        assertEquals(ActivePlayEarner.Outcome.Kind.DISABLED, out.kind());
    }

    // EA-006 — admin-grants tellen niet mee voor de active-play daily cap.
    @Test
    void adminGrantDoesNotConsumeCap() {
        UUID p = UUID.randomUUID();
        new ClaimBlockAdminOps(ledger).grantToPlayer(p, 999, "ADMIN:t");
        var e = earner(10, 25);
        var t = Instant.parse("2026-07-21T12:00:00Z");
        assertEquals(0, e.earnedToday(p, t), "admin-grant zit niet in earning-state");
        assertEquals(25, e.dailyCapRemaining(p, t));
    }

    // EA-007 — negatieve config afgewezen.
    @Test
    void negativeConfigRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivePlayEarner.EarningConfig(-1, 300, 500));
    }

    // EA-008 — ledger reason is ACTIVE_PLAY_EARN.
    @Test
    void ledgerReasonIsActivePlay() {
        UUID p = UUID.randomUUID();
        earner(4, 500).award(p, Instant.parse("2026-07-21T12:00:00Z"), 1);
        String reason = db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT reason FROM claim_block_ledger WHERE holder_id = ?")) {
                ps.setString(1, p.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next(); return rs.getString(1);
                }
            }
        });
        assertEquals(ClaimBlockReason.ACTIVE_PLAY_EARN.name(), reason);
    }
}
