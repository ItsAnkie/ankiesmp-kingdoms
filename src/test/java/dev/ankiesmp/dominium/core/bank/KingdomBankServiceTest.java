package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.storage.bank.SqlBankStore;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.kingdom.SqlKingdomStore;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomBankServiceTest {

    @TempDir Path tempDir;
    private Database db;
    private BankStore bankStore;
    private KingdomService kingdomService;
    private UUID kingdomId;
    private UUID leader;
    private UUID member;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("bank.db").toAbsolutePath());
        new MigrationRunner(db).migrate();
        var kStore = new SqlKingdomStore(db);
        kingdomService = new KingdomService(kStore, Clock.systemUTC(),
                new KingdomService.NameConfig(3, 24), KingdomService.Invalidator.NOOP);
        leader = UUID.randomUUID();
        member = UUID.randomUUID();
        var k = kingdomService.create("Alpha", leader).value();
        kingdomId = k.id();
        // Invite + accept member
        var invite = new dev.ankiesmp.dominium.core.kingdom.KingdomInviteService(
                kStore, Clock.systemUTC(), java.time.Duration.ofMinutes(10),
                KingdomService.Invalidator.NOOP);
        invite.invite(leader, member);
        invite.accept(member, kingdomId);
        bankStore = new SqlBankStore(db);
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private KingdomBankService serviceWith(VaultAdapter vault) {
        return new KingdomBankService(bankStore, kingdomService, vault, Clock.systemUTC(),
                1_000_000_000L);
    }

    // KB-001 — deposit zonder Vault → VAULT_UNAVAILABLE, geen saldo-mutatie.
    @Test
    void depositWithoutVaultRejected() {
        var svc = serviceWith(VaultAdapter.NO_OP);
        var r = svc.deposit(member, 500);
        assertEquals(KingdomBankService.Kind.VAULT_UNAVAILABLE, r.kind());
        assertEquals(0L, svc.balance(kingdomId));
    }

    // KB-002 — leader withdraw zonder saldo → INSUFFICIENT_BANK.
    @Test
    void withdrawInsufficientBankRejected() {
        var svc = serviceWith(new FakeVault(true, true));
        var r = svc.withdraw(leader, 500);
        assertEquals(KingdomBankService.Kind.INSUFFICIENT_BANK, r.kind());
        assertEquals(0L, svc.balance(kingdomId));
    }

    // KB-003 — happy-path deposit + withdraw met werkende Vault.
    @Test
    void memberDepositLeaderWithdraw() {
        var svc = serviceWith(new FakeVault(true, true));
        assertEquals(KingdomBankService.Kind.OK, svc.deposit(member, 1000).kind());
        assertEquals(1000L, svc.balance(kingdomId));
        assertEquals(KingdomBankService.Kind.OK, svc.withdraw(leader, 400).kind());
        assertEquals(600L, svc.balance(kingdomId));
    }

    // KB-004 — member withdraw geweigerd (NOT_ALLOWED).
    @Test
    void memberWithdrawRejected() {
        var svc = serviceWith(new FakeVault(true, true));
        svc.deposit(member, 500);
        var r = svc.withdraw(member, 100);
        assertEquals(KingdomBankService.Kind.NOT_ALLOWED, r.kind());
        assertEquals(500L, svc.balance(kingdomId));
    }

    // KB-005 — Vault withdraw faalt → deposit blijft PENDING → FAILED, saldo 0.
    @Test
    void vaultWithdrawFailedNoBankChange() {
        var svc = serviceWith(new FakeVault(false, true));
        var r = svc.deposit(leader, 500);
        assertEquals(KingdomBankService.Kind.INSUFFICIENT_PLAYER, r.kind());
        assertEquals(0L, svc.balance(kingdomId));
    }

    // KB-006 — withdraw + Vault deposit faalt → auto-refund bank, COMPENSATED.
    @Test
    void withdrawVaultDepositFailsRollsBackBank() {
        var vault = new FakeVault(true, false);
        var svc = serviceWith(vault);
        // Seed via ok-vault:
        var seedSvc = serviceWith(new FakeVault(true, true));
        seedSvc.deposit(member, 1000);
        assertEquals(1000L, svc.balance(kingdomId));
        var r = svc.withdraw(leader, 400);
        assertEquals(KingdomBankService.Kind.COMPENSATED, r.kind());
        assertEquals(1000L, svc.balance(kingdomId), "bank saldo intact na rollback");
    }

    // KB-007 — invalid amounts.
    @Test
    void invalidAmountsRejected() {
        var svc = serviceWith(new FakeVault(true, true));
        assertEquals(KingdomBankService.Kind.INVALID_AMOUNT, svc.deposit(member, 0).kind());
        assertEquals(KingdomBankService.Kind.INVALID_AMOUNT, svc.deposit(member, -1).kind());
        assertEquals(KingdomBankService.Kind.INVALID_AMOUNT, svc.withdraw(leader, 0).kind());
    }

    // KB-008 — non-member deposit → NOT_A_MEMBER.
    @Test
    void nonMemberRejected() {
        var svc = serviceWith(new FakeVault(true, true));
        assertEquals(KingdomBankService.Kind.NOT_A_MEMBER,
                svc.deposit(UUID.randomUUID(), 500).kind());
    }

    // KB-009 — internal purchase debit (buy-claimblocks path) werkt zonder Vault.
    @Test
    void internalPurchaseDebit() {
        var svc = serviceWith(new FakeVault(true, true));
        svc.deposit(member, 5000);
        var r = svc.debitForInternalPurchase(kingdomId, leader, 1200, "test");
        assertEquals(KingdomBankService.Kind.OK, r.kind());
        assertEquals(3800L, svc.balance(kingdomId));
    }

    // KB-010 — recovery: PENDING operation wordt COMPENSATION_REQUIRED bij restart.
    @Test
    void startupRecoveryMarksIncompletePending() {
        // Force een PENDING op via direct journal write.
        var op = new BankOperation(UUID.randomUUID(), kingdomId,
                BankOperation.Kind.DEPOSIT, 100, BankOperation.State.PENDING,
                leader, java.util.Optional.of("stuck"),
                java.time.Instant.now(), java.time.Instant.now());
        bankStore.applyBalanceChangeWithJournal(op, 0L, 1_000_000_000L);
        var svc = serviceWith(VaultAdapter.NO_OP);
        int touched = svc.recoverIncompleteOperations();
        assertTrue(touched >= 1);
        var found = bankStore.findOperation(op.correlationId()).orElseThrow();
        // Vanaf de fase-5-refactor: PENDING → FAILED (Vault heeft nog niets gedaan);
        // EXTERNAL_APPLIED → COMPENSATION_REQUIRED (Vault al gemuteerd).
        assertEquals(BankOperation.State.FAILED, found.state());
    }

    /** Testdouble voor Vault. */
    static final class FakeVault implements VaultAdapter {
        final boolean withdrawOk, depositOk;
        FakeVault(boolean w, boolean d) { withdrawOk = w; depositOk = d; }
        @Override public boolean available() { return true; }
        @Override public boolean withdrawPlayer(UUID p, long a) { return withdrawOk; }
        @Override public boolean depositPlayer(UUID p, long a) { return depositOk; }
    }
}
