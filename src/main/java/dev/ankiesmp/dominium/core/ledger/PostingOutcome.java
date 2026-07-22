package dev.ankiesmp.dominium.core.ledger;

/**
 * Resultaat van een {@link ClaimBlockLedger#post(PostingRequest)}-aanroep.
 * Bevat een {@link Kind} plus de meest recente {@link BalanceSnapshot}
 * (indien beschikbaar) zodat de aanroeper niet opnieuw hoeft te querien.
 */
public final class PostingOutcome {

    public enum Kind {
        APPLIED,
        ALREADY_APPLIED,
        INSUFFICIENT_BALANCE
    }

    private final Kind kind;
    private final BalanceSnapshot balance;
    private final LedgerEntry entry;

    private PostingOutcome(Kind kind, BalanceSnapshot balance, LedgerEntry entry) {
        this.kind = kind;
        this.balance = balance;
        this.entry = entry;
    }

    public static PostingOutcome applied(BalanceSnapshot balance, LedgerEntry entry) {
        return new PostingOutcome(Kind.APPLIED, balance, entry);
    }

    public static PostingOutcome alreadyApplied(BalanceSnapshot balance, LedgerEntry entry) {
        return new PostingOutcome(Kind.ALREADY_APPLIED, balance, entry);
    }

    public static PostingOutcome insufficientBalance(BalanceSnapshot balance) {
        return new PostingOutcome(Kind.INSUFFICIENT_BALANCE, balance, null);
    }

    public Kind kind() { return kind; }
    public BalanceSnapshot balance() { return balance; }
    public LedgerEntry entry() { return entry; }
    public boolean isSuccess() { return kind == Kind.APPLIED || kind == Kind.ALREADY_APPLIED; }
}
