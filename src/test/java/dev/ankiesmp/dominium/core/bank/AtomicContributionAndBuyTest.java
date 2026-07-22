package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.kingdom.KingdomInviteService;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;
import dev.ankiesmp.dominium.core.ledger.TransferOutcome;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AtomicContributionAndBuyTest {

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
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("atomic.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        var kStore = new SqlKingdomStore(db);
        kingdomService = new KingdomService(kStore, Clock.systemUTC(),
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        leader = UUID.randomUUID();
        member = UUID.randomUUID();
        kingdomId = kingdomService.create("Alpha", leader).value().id();
        var invite = new KingdomInviteService(kStore, Clock.systemUTC(),
                java.time.Duration.ofMinutes(10), KingdomService.Invalidator.NOOP);
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

    private long countRowsFor(HolderKey h) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claim_block_ledger WHERE holder_type=? AND holder_id=?")) {
                ps.setString(1, h.type().name());
                ps.setString(2, h.id().toString());
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
            }
        });
    }

    // AC-001 — atomicTransfer: beide posts committen, totaal blijft gelijk.
    @Test
    void atomicTransferPreservesTotalBlocks() {
        UUID a = UUID.randomUUID();
        UUID kingdom = UUID.randomUUID();
        grantPersonal(a, 500);
        var out = ledger.atomicTransfer(
                PostingRequest.builder().holder(HolderKey.player(a)).delta(-200)
                        .reason(ClaimBlockReason.DONATION_OUT)
                        .idempotencyKey(UUID.randomUUID()).build(),
                PostingRequest.builder().holder(HolderKey.kingdom(kingdom)).delta(200)
                        .reason(ClaimBlockReason.DONATION_IN)
                        .idempotencyKey(UUID.randomUUID()).build());
        assertEquals(TransferOutcome.Kind.APPLIED, out.kind());
        assertEquals(300L, ledger.balanceOrZero(HolderKey.player(a)).balance());
        assertEquals(200L, ledger.balanceOrZero(HolderKey.kingdom(kingdom)).balance());
    }

    // AC-002 — atomicTransfer bij insufficient personal: geen post, geen mutatie.
    @Test
    void atomicTransferInsufficientRollsBackBoth() {
        UUID a = UUID.randomUUID();
        UUID kingdom = UUID.randomUUID();
        var out = ledger.atomicTransfer(
                PostingRequest.builder().holder(HolderKey.player(a)).delta(-500)
                        .reason(ClaimBlockReason.DONATION_OUT)
                        .idempotencyKey(UUID.randomUUID()).build(),
                PostingRequest.builder().holder(HolderKey.kingdom(kingdom)).delta(500)
                        .reason(ClaimBlockReason.DONATION_IN)
                        .idempotencyKey(UUID.randomUUID()).build());
        assertEquals(TransferOutcome.Kind.INSUFFICIENT_BALANCE, out.kind());
        assertEquals(0L, ledger.balanceOrZero(HolderKey.player(a)).balance());
        assertEquals(0L, ledger.balanceOrZero(HolderKey.kingdom(kingdom)).balance());
        assertEquals(0L, countRowsFor(HolderKey.player(a)));
        assertEquals(0L, countRowsFor(HolderKey.kingdom(kingdom)));
    }

    // AC-003 — pool.contribute is nu atomair: bij insufficient personal geen enkele rij.
    @Test
    void contributeAtomicNoPartialRows() {
        var r = pool.contribute(member, 100);
        assertEquals(KingdomClaimBlockPoolService.Kind.INSUFFICIENT_PERSONAL, r.kind());
        assertEquals(0L, countRowsFor(HolderKey.player(member)));
        assertEquals(0L, countRowsFor(HolderKey.kingdom(kingdomId)));
    }

    // AC-004 — pool.contribute happy path: één row per holder, totaal blijft.
    @Test
    void contributeHappyPathPreservesTotal() {
        grantPersonal(member, 500);
        long grantRows = countRowsFor(HolderKey.player(member));
        var r = pool.contribute(member, 200);
        assertEquals(KingdomClaimBlockPoolService.Kind.OK, r.kind());
        assertEquals(300L, ledger.balanceOrZero(HolderKey.player(member)).balance());
        assertEquals(200L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
        assertEquals(grantRows + 1, countRowsFor(HolderKey.player(member)));
        assertEquals(1L, countRowsFor(HolderKey.kingdom(kingdomId)));
    }

    // AC-005 — buy is atomair: bank-debit én ledger-credit in één tx.
    @Test
    void buyAtomicDebitAndCredit() {
        bankService.deposit(member, 50_000L);
        long bankBefore = bankService.balance(kingdomId);
        long ledgerBefore = ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance();
        var r = pool.buy(leader, 5); // cost = 5 * 1000 = 5000
        assertEquals(KingdomClaimBlockPoolService.Kind.OK, r.kind());
        assertEquals(bankBefore - 5000L, bankService.balance(kingdomId));
        assertEquals(ledgerBefore + 5L, ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance());
    }

    // AC-006 — buy: insufficient bank → geen ledger-mutatie, geen bank-debit.
    @Test
    void buyInsufficientBankNoLedgerRow() {
        long ledgerBefore = countRowsFor(HolderKey.kingdom(kingdomId));
        var r = pool.buy(leader, 5);
        assertEquals(KingdomClaimBlockPoolService.Kind.INSUFFICIENT_BANK, r.kind());
        assertEquals(0L, bankService.balance(kingdomId));
        assertEquals(ledgerBefore, countRowsFor(HolderKey.kingdom(kingdomId)));
    }

    // AC-007 — atomicDebitWithLedger: callback returned false → volledige rollback.
    @Test
    void atomicDebitCallbackRefusalRollsBackEverything() {
        bankService.deposit(member, 50_000L);
        long bankBefore = bankService.balance(kingdomId);
        var r = bankService.atomicDebitWithLedger(kingdomId, leader, 1000L, "test-rollback",
                conn -> false);
        assertEquals(KingdomBankService.Kind.INSUFFICIENT_BANK, r.kind());
        assertEquals(bankBefore, bankService.balance(kingdomId),
                "bank ongewijzigd na callback-refusal");
    }

    // AC-008 — atomicDebitWithLedger: callback gooit → volledige rollback.
    @Test
    void atomicDebitCallbackThrowRollsBack() {
        bankService.deposit(member, 50_000L);
        long bankBefore = bankService.balance(kingdomId);
        try {
            bankService.atomicDebitWithLedger(kingdomId, leader, 1000L, "test-throw", conn -> {
                throw new RuntimeException("injected failure");
            });
        } catch (RuntimeException ignored) { }
        assertEquals(bankBefore, bankService.balance(kingdomId),
                "bank ongewijzigd na callback-throw");
    }
}
