package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.core.kingdom.KingdomCapability;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application-service voor kingdom bank. Alle bedragen in minor units.
 *
 * <p>Vault-flow is journaled: PENDING → EXTERNAL_APPLIED → COMMITTED. Bij
 * DB-fout na Vault-mutatie proberen we auto-refund (COMPENSATED); faalt dat
 * dan blijft de operation in COMPENSATION_REQUIRED voor admin-review.
 *
 * <p>Zonder Vault ({@link VaultAdapter#available()} = false) blijft view
 * werken en geven deposit/withdraw een Kind.VAULT_UNAVAILABLE terug — de
 * kingdom-interne buy-claimblocks blijft wel bruikbaar.
 */
public final class KingdomBankService {

    public enum Kind {
        OK, NOT_A_MEMBER, NOT_ALLOWED, INVALID_AMOUNT, MAX_BALANCE_EXCEEDED,
        INSUFFICIENT_BANK, INSUFFICIENT_PLAYER, VAULT_UNAVAILABLE,
        COMPENSATED, COMPENSATION_REQUIRED
    }

    public record Result(Kind kind, long newBalanceMinor, UUID correlationId, String message) {
        public static Result ok(long bal, UUID id) { return new Result(Kind.OK, bal, id, null); }
        public static Result error(Kind k, String msg) { return new Result(k, -1L, null, msg); }
    }

    private final BankStore store;
    private final KingdomService kingdomService;
    private final VaultAdapter vault;
    private final Clock clock;
    private final long maxBalance;

    public KingdomBankService(BankStore store, KingdomService kingdomService,
                              VaultAdapter vault, Clock clock, long maxBalance) {
        this.store = Objects.requireNonNull(store);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.vault = Objects.requireNonNull(vault);
        this.clock = Objects.requireNonNull(clock);
        if (maxBalance <= 0) throw new IllegalArgumentException("maxBalance must be > 0");
        this.maxBalance = maxBalance;
    }

    public long balance(UUID kingdomId) {
        Objects.requireNonNull(kingdomId);
        return store.balance(kingdomId);
    }

    public Result deposit(UUID actor, long amountMinor) {
        Objects.requireNonNull(actor);
        if (amountMinor <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        Optional<KingdomMember> m = kingdomService.membershipFor(actor);
        if (m.isEmpty()) return Result.error(Kind.NOT_A_MEMBER, "not in kingdom");
        if (!KingdomCapability.allowed(m.get().role(), KingdomCapability.DEPOSIT_BANK)) {
            return Result.error(Kind.NOT_ALLOWED, "no permission to deposit");
        }
        if (!vault.available()) {
            return Result.error(Kind.VAULT_UNAVAILABLE, "Vault is not installed");
        }
        UUID corr = UUID.randomUUID();
        UUID kingdomId = m.get().kingdomId();
        Instant now = Instant.ofEpochMilli(clock.millis());
        // 1. journal PENDING
        recordOp(new BankOperation(corr, kingdomId, BankOperation.Kind.DEPOSIT, amountMinor,
                BankOperation.State.PENDING, actor, Optional.empty(), now, now));
        // 2. Vault withdraw player
        if (!vault.withdrawPlayer(actor, amountMinor)) {
            store.updateOperationState(corr, BankOperation.State.FAILED,
                    "vault withdraw refused", clock.millis());
            return Result.error(Kind.INSUFFICIENT_PLAYER, "vault withdraw refused");
        }
        store.updateOperationState(corr, BankOperation.State.EXTERNAL_APPLIED,
                "vault withdraw ok", clock.millis());
        // 3. credit bank
        boolean ok;
        try {
            ok = store.applyBalanceChangeWithJournal(
                    new BankOperation(corr, kingdomId, BankOperation.Kind.DEPOSIT, amountMinor,
                            BankOperation.State.COMMITTED, actor,
                            Optional.of("deposit"), now, Instant.ofEpochMilli(clock.millis())),
                    amountMinor, maxBalance);
        } catch (RuntimeException ex) {
            ok = false;
        }
        if (!ok) {
            // 4. auto-refund
            boolean refunded = vault.depositPlayer(actor, amountMinor);
            store.updateOperationState(corr,
                    refunded ? BankOperation.State.COMPENSATED
                            : BankOperation.State.COMPENSATION_REQUIRED,
                    refunded ? "auto-refunded after DB credit failure"
                            : "REFUND FAILED — admin must resolve",
                    clock.millis());
            return Result.error(refunded ? Kind.COMPENSATED : Kind.COMPENSATION_REQUIRED,
                    "bank credit failed");
        }
        return Result.ok(store.balance(kingdomId), corr);
    }

    public Result withdraw(UUID actor, long amountMinor) {
        Objects.requireNonNull(actor);
        if (amountMinor <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        Optional<KingdomMember> m = kingdomService.membershipFor(actor);
        if (m.isEmpty()) return Result.error(Kind.NOT_A_MEMBER, "not in kingdom");
        if (!KingdomCapability.allowed(m.get().role(), KingdomCapability.WITHDRAW_BANK)) {
            return Result.error(Kind.NOT_ALLOWED, "no permission to withdraw");
        }
        if (!vault.available()) {
            return Result.error(Kind.VAULT_UNAVAILABLE, "Vault is not installed");
        }
        UUID corr = UUID.randomUUID();
        UUID kingdomId = m.get().kingdomId();
        Instant now = Instant.ofEpochMilli(clock.millis());
        // 1. atomair debiteer bank + journal PENDING
        boolean debited = store.applyBalanceChangeWithJournal(
                new BankOperation(corr, kingdomId, BankOperation.Kind.WITHDRAW, amountMinor,
                        BankOperation.State.PENDING, actor, Optional.of("withdraw"), now, now),
                -amountMinor, maxBalance);
        if (!debited) return Result.error(Kind.INSUFFICIENT_BANK, "insufficient bank balance");
        // 2. Vault deposit naar speler
        boolean deposited = vault.depositPlayer(actor, amountMinor);
        if (!deposited) {
            // 3. Rollback
            boolean rolled = store.applyBalanceChangeWithJournal(
                    new BankOperation(UUID.randomUUID(), kingdomId,
                            BankOperation.Kind.DEPOSIT, amountMinor,
                            BankOperation.State.COMPENSATED, actor,
                            Optional.of("withdraw rollback"), now, Instant.ofEpochMilli(clock.millis())),
                    amountMinor, maxBalance);
            store.updateOperationState(corr,
                    rolled ? BankOperation.State.COMPENSATED
                            : BankOperation.State.COMPENSATION_REQUIRED,
                    rolled ? "vault deposit failed — bank refunded"
                            : "vault + bank rollback both failed",
                    clock.millis());
            return Result.error(rolled ? Kind.COMPENSATED : Kind.COMPENSATION_REQUIRED,
                    "vault deposit failed");
        }
        store.updateOperationState(corr, BankOperation.State.COMMITTED,
                "withdraw committed", clock.millis());
        return Result.ok(store.balance(kingdomId), corr);
    }

    /**
     * Interne bank→claimblock aankoop: geen Vault-call, één DB-transactie.
     * Retourneert de correlation-ID op succes, of een fout-Kind.
     */
    public Result debitForInternalPurchase(UUID kingdomId, UUID actor, long amountMinor,
                                           String reason) {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(actor);
        if (amountMinor <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        UUID corr = UUID.randomUUID();
        Instant now = Instant.ofEpochMilli(clock.millis());
        boolean ok = store.applyBalanceChangeWithJournal(
                new BankOperation(corr, kingdomId, BankOperation.Kind.BUY_CLAIMBLOCKS, amountMinor,
                        BankOperation.State.COMMITTED, actor, Optional.of(reason), now, now),
                -amountMinor, maxBalance);
        if (!ok) return Result.error(Kind.INSUFFICIENT_BANK, "insufficient bank balance");
        return Result.ok(store.balance(kingdomId), corr);
    }

    /**
     * Atomair: bank-debit + gebruiker-callback binnen dezelfde DB-transactie.
     * Wanneer de callback {@code false} teruggeeft of gooit, rolt de hele tx
     * terug — bank ongewijzigd, geen ledger-post. Gebruikt door {@code buy}
     * om bank-debit + kingdom-ledger-credit strikt atomair uit te voeren.
     */
    public Result atomicDebitWithLedger(UUID kingdomId, UUID actor, long amountMinor,
                                        String reason, BankStore.LedgerPostInTx ledgerCallback) {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(ledgerCallback);
        if (amountMinor <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        UUID corr = UUID.randomUUID();
        Instant now = Instant.ofEpochMilli(clock.millis());
        boolean ok = store.atomicDebitWithLedger(
                new BankOperation(corr, kingdomId, BankOperation.Kind.BUY_CLAIMBLOCKS, amountMinor,
                        BankOperation.State.COMMITTED, actor, Optional.of(reason), now, now),
                amountMinor, maxBalance, ledgerCallback);
        if (!ok) return Result.error(Kind.INSUFFICIENT_BANK,
                "insufficient bank balance or ledger callback refused");
        return Result.ok(store.balance(kingdomId), corr);
    }

    /**
     * Startup recovery:
     * <ul>
     *   <li>{@link BankOperation.State#PENDING}: geen externe Vault-mutatie
     *       gebeurd → veilig als {@link BankOperation.State#FAILED} markeren.</li>
     *   <li>{@link BankOperation.State#EXTERNAL_APPLIED}: Vault heeft al
     *       afgeschreven/uitbetaald → {@link BankOperation.State#COMPENSATION_REQUIRED}
     *       voor handmatige review. Nooit blind opnieuw Vault muteren.</li>
     * </ul>
     */
    public int recoverIncompleteOperations() {
        int touched = 0;
        for (BankOperation op : store.listIncomplete()) {
            BankOperation.State target;
            String reason;
            if (op.state() == BankOperation.State.PENDING) {
                // Externe Vault-stap is niet uitgevoerd → veilig FAILED.
                target = BankOperation.State.FAILED;
                reason = "restart during PENDING — no external mutation performed";
            } else if (op.state() == BankOperation.State.EXTERNAL_APPLIED) {
                // Vault heeft al iets gedaan → handmatige compensatie.
                target = BankOperation.State.COMPENSATION_REQUIRED;
                reason = "restart during EXTERNAL_APPLIED — external side already mutated, needs review";
            } else {
                target = BankOperation.State.COMPENSATION_REQUIRED;
                reason = "restart in unexpected state " + op.state();
            }
            store.updateOperationState(op.correlationId(), target, reason, clock.millis());
            touched++;
        }
        return touched;
    }

    public long maxBalance() { return maxBalance; }

    private void recordOp(BankOperation op) {
        // Journal-only, geen saldo-mutatie: applyBalanceChangeWithJournal met
        // delta 0 werkt hier niet want die zou de saldo-row touchen; gebruik
        // een 0-delta journal-only helper via de store's update.
        // Hier: eerste insert via applyBalanceChangeWithJournal met delta 0 op
        // een niet-bestaande row zou balance_minor=0 seeden — dat is prima.
        store.applyBalanceChangeWithJournal(op, 0L, maxBalance);
    }
}
