package dev.ankiesmp.dominium.core.bank;

import dev.ankiesmp.dominium.api.ClaimBlockReason;
import dev.ankiesmp.dominium.core.common.HolderKey;
import dev.ankiesmp.dominium.core.kingdom.KingdomCapability;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomService;
import dev.ankiesmp.dominium.core.ledger.ClaimBlockLedger;
import dev.ankiesmp.dominium.core.ledger.PostingOutcome;
import dev.ankiesmp.dominium.core.ledger.PostingRequest;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Contribute (PERSONAL → KINGDOM claimblock-transfer) en Buy (kingdom
 * bank → kingdom claimblocks) via de bestaande append-only ledger.
 * Geen tweede claimblock-saldo; kingdom gebruikt {@link HolderKey#kingdom}.
 */
public final class KingdomClaimBlockPoolService {

    public enum Kind {
        OK, NOT_A_MEMBER, NOT_ALLOWED, INSUFFICIENT_PERSONAL, INSUFFICIENT_BANK,
        DISABLED, INVALID_AMOUNT, MAX_PER_OP_EXCEEDED
    }

    public record Result(Kind kind, long kingdomBalance, long personalBalance,
                         UUID correlationId, String message) {
        public static Result ok(long k, long p, UUID id) { return new Result(Kind.OK, k, p, id, null); }
        public static Result error(Kind k, String m) { return new Result(k, -1L, -1L, null, m); }
    }

    public record Config(boolean contributionEnabled, boolean purchasingEnabled,
                         long pricePerBlockMinor, long maxPurchasePerOperation) {
        public Config {
            if (pricePerBlockMinor <= 0) throw new IllegalArgumentException("price must be > 0");
            if (maxPurchasePerOperation <= 0) throw new IllegalArgumentException("max/op must be > 0");
        }
    }

    private final ClaimBlockLedger ledger;
    private final KingdomBankService bankService;
    private final KingdomService kingdomService;
    private final Config config;

    public KingdomClaimBlockPoolService(ClaimBlockLedger ledger, KingdomBankService bankService,
                                        KingdomService kingdomService, Config config) {
        this.ledger = Objects.requireNonNull(ledger);
        this.bankService = Objects.requireNonNull(bankService);
        this.kingdomService = Objects.requireNonNull(kingdomService);
        this.config = Objects.requireNonNull(config);
    }

    public Result contribute(UUID actor, long blocks) {
        if (!config.contributionEnabled()) return Result.error(Kind.DISABLED, "contributions disabled");
        if (blocks <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        Optional<KingdomMember> m = kingdomService.membershipFor(actor);
        if (m.isEmpty()) return Result.error(Kind.NOT_A_MEMBER, "not in kingdom");
        if (!KingdomCapability.allowed(m.get().role(), KingdomCapability.CONTRIBUTE_CLAIMBLOCKS)) {
            return Result.error(Kind.NOT_ALLOWED, "no permission to contribute");
        }
        UUID kingdomId = m.get().kingdomId();
        UUID correlation = UUID.randomUUID();
        // Atomair: PERSONAL debit + KINGDOM credit binnen één SQLite-transactie.
        UUID spendKey = UUID.nameUUIDFromBytes(
                ("contribute-spend:" + correlation).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        UUID creditKey = UUID.nameUUIDFromBytes(
                ("contribute-credit:" + correlation).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var debit = PostingRequest.builder()
                .holder(HolderKey.player(actor))
                .delta(-blocks)
                .reason(ClaimBlockReason.DONATION_OUT)
                .reference("contribute:" + correlation)
                .idempotencyKey(spendKey)
                .actor(actor.toString())
                .build();
        var credit = PostingRequest.builder()
                .holder(HolderKey.kingdom(kingdomId))
                .delta(blocks)
                .reason(ClaimBlockReason.DONATION_IN)
                .reference("contribute:" + correlation)
                .idempotencyKey(creditKey)
                .actor(actor.toString())
                .build();
        var out = ledger.atomicTransfer(debit, credit);
        if (out.kind() == dev.ankiesmp.dominium.core.ledger.TransferOutcome.Kind.INSUFFICIENT_BALANCE) {
            return Result.error(Kind.INSUFFICIENT_PERSONAL, "insufficient personal claim blocks");
        }
        long kBal = out.creditBalance() == null
                ? ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance()
                : out.creditBalance().balance();
        long pBal = out.debitBalance() == null
                ? ledger.balanceOrZero(HolderKey.player(actor)).balance()
                : out.debitBalance().balance();
        return Result.ok(kBal, pBal, correlation);
    }

    // (contribute is nu atomair via ledger.atomicTransfer — geen compensatie-pad meer)

    public Result buy(UUID actor, long blocks) {
        if (!config.purchasingEnabled()) return Result.error(Kind.DISABLED, "purchasing disabled");
        if (blocks <= 0) return Result.error(Kind.INVALID_AMOUNT, "amount must be > 0");
        if (blocks > config.maxPurchasePerOperation()) {
            return Result.error(Kind.MAX_PER_OP_EXCEEDED,
                    "max " + config.maxPurchasePerOperation() + " blocks per operation");
        }
        Optional<KingdomMember> m = kingdomService.membershipFor(actor);
        if (m.isEmpty()) return Result.error(Kind.NOT_A_MEMBER, "not in kingdom");
        if (!KingdomCapability.allowed(m.get().role(), KingdomCapability.BUY_CLAIMBLOCKS)) {
            return Result.error(Kind.NOT_ALLOWED, "no permission to buy claim blocks");
        }
        long cost;
        try {
            cost = Math.multiplyExact(blocks, config.pricePerBlockMinor());
        } catch (ArithmeticException ex) {
            return Result.error(Kind.INVALID_AMOUNT, "cost overflow");
        }
        UUID kingdomId = m.get().kingdomId();
        // Atomair: bank-debit + ledger-credit binnen exact één DB-transactie.
        // De ledger-post gebruikt de SqlClaimBlockLedger#postInConnection via
        // een lambda die op de gedeelde Connection werkt.
        UUID ledgerKey = UUID.nameUUIDFromBytes(
                ("kingdom-buy:" + UUID.randomUUID()).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        var req = PostingRequest.builder()
                .holder(HolderKey.kingdom(kingdomId))
                .delta(blocks)
                .reason(ClaimBlockReason.EXTERNAL_REWARD)
                .reference("kingdom-buy:" + ledgerKey)
                .idempotencyKey(ledgerKey)
                .actor(actor.toString())
                .build();
        var sqlLedger = (ledger instanceof dev.ankiesmp.dominium.storage.ledger.SqlClaimBlockLedger sc)
                ? sc : null;
        BankStore.LedgerPostInTx callback = sqlLedger != null
                ? (conn -> {
                        PostingOutcome pout = sqlLedger.postInConnection(conn, req);
                        return pout.kind() != PostingOutcome.Kind.INSUFFICIENT_BALANCE;
                    })
                : (conn -> false); // in-memory ledgers: geen atomicity mogelijk
        var debit = bankService.atomicDebitWithLedger(kingdomId, actor, cost,
                "buy " + blocks + " claim blocks", callback);
        if (debit.kind() != KingdomBankService.Kind.OK) {
            return Result.error(Kind.INSUFFICIENT_BANK, "insufficient bank balance");
        }
        long kBal = ledger.balanceOrZero(HolderKey.kingdom(kingdomId)).balance();
        return Result.ok(kBal, -1L, debit.correlationId());
    }

    public Config config() { return config; }
}
