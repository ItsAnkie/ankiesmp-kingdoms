package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.storage.bank.SqlBankStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomClaimBlockPoolServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private ClaimBlockLedger ledger;
    private KingdomBankService bankService;
    private KingdomService kingdomService;
    private KingdomClaimBlockPoolService pool;
    private UUID kingdomId;
    private UUID leader;
    private UUID member;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("pool.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        var kStore = new SqlKingdomStore(db);
        kingdomService = new KingdomService(kStore, Clock.systemUTC(),
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        leader = UUID.randomUUID();
        member = UUID.randomUUID();
        kingdomId = kingdomService.create("Alpha", leader).value().id();
        var invite = new dev.ankiesmp.dominium.core.kingdom.KingdomInviteService(
                kStore, Clock.systemUTC(), java.time.Duration.ofMinutes(10),
                KingdomService.Invalidator.NOOP);
        invite.invite(leader, member);
        invite.accept(member, kingdomId);
        ledger = new SqlClaimBlockLedger(db);
        bankService = new KingdomBankService(new SqlBankStore(db), kingdomService,
                new KingdomBankServiceTest.FakeVault(true, true), Clock.systemUTC(),
                1_000_000_000L);
        pool = new KingdomClaimBlockPoolService(ledger, bankService, kingdomService,
                new KingdomClaimBlockPoolService.Config(true, true, 1000L, 5000L));
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void grantPersonal(UUID p, long blocks) {
        ledger.post(PostingRequest.builder()
                .holder(HolderKey.player(p)).delta(blocks)
                .reason(ClaimBlockReason.ADMIN_GRANT)
                .idempotencyKey(UUID.randomUUID()).build());
    }

    // KP-001 — contribute: personal→kingdom, totaal blijft gelijk.
    @Test
    void contributeMovesBlocksFromPersonalToKingdom() {
        grantPersonal(member, 500);
        var r = pool.contribute(member, 200);
        assertEquals(KingdomClaimBlockPoolService.Kind.OK, r.kind());
        assertEquals(300L, ledger.balanceOrZero(HolderKey.player(member)).balance());
        assertEquals(200L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
    }

    // KP-002 — insufficient personal.
    @Test
    void contributeInsufficientPersonal() {
        var r = pool.contribute(member, 100);
        assertEquals(KingdomClaimBlockPoolService.Kind.INSUFFICIENT_PERSONAL, r.kind());
    }

    // KP-003 — non-member weigert.
    @Test
    void contributeNotAMember() {
        var r = pool.contribute(UUID.randomUUID(), 100);
        assertEquals(KingdomClaimBlockPoolService.Kind.NOT_A_MEMBER, r.kind());
    }

    // KP-004 — invalid amount.
    @Test
    void contributeInvalidAmount() {
        assertEquals(KingdomClaimBlockPoolService.Kind.INVALID_AMOUNT,
                pool.contribute(member, 0).kind());
        assertEquals(KingdomClaimBlockPoolService.Kind.INVALID_AMOUNT,
                pool.contribute(member, -5).kind());
    }

    // KP-005 — buy leader: bank debit + kingdom claimblock credit.
    @Test
    void buyDrawsFromBankNotPersonal() {
        // Seed bank via deposit.
        bankService.deposit(member, 50_000L); // 50 000 minor
        assertEquals(50_000L, bankService.balance(kingdomId));
        var r = pool.buy(leader, 10); // cost = 10 * 1000 = 10 000 minor
        assertEquals(KingdomClaimBlockPoolService.Kind.OK, r.kind());
        assertEquals(40_000L, bankService.balance(kingdomId));
        assertEquals(10L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
        // Persoonlijk saldo van leader ONVERANDERD.
        assertEquals(0L, ledger.balanceOrZero(HolderKey.player(leader)).balance());
    }

    // KP-006 — buy insufficient bank balance.
    @Test
    void buyInsufficientBank() {
        var r = pool.buy(leader, 100);
        assertEquals(KingdomClaimBlockPoolService.Kind.INSUFFICIENT_BANK, r.kind());
    }

    // KP-007 — member cannot buy (only leader/co-leader).
    @Test
    void memberCannotBuy() {
        bankService.deposit(member, 50_000L);
        var r = pool.buy(member, 5);
        assertEquals(KingdomClaimBlockPoolService.Kind.NOT_ALLOWED, r.kind());
    }

    // KP-008 — max per operation.
    @Test
    void maxPerOperationEnforced() {
        bankService.deposit(member, 1_000_000L);
        var r = pool.buy(leader, 6000);
        assertEquals(KingdomClaimBlockPoolService.Kind.MAX_PER_OP_EXCEEDED, r.kind());
    }
}
