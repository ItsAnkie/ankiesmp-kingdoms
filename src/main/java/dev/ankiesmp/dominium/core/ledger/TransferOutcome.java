package dev.ankiesmp.dominium.core.ledger;

public record TransferOutcome(Kind kind, BalanceSnapshot debitBalance, BalanceSnapshot creditBalance) {
    public enum Kind { APPLIED, ALREADY_APPLIED, INSUFFICIENT_BALANCE }
    public static TransferOutcome applied(BalanceSnapshot d, BalanceSnapshot c) {
        return new TransferOutcome(Kind.APPLIED, d, c);
    }
    public static TransferOutcome alreadyApplied(BalanceSnapshot d, BalanceSnapshot c) {
        return new TransferOutcome(Kind.ALREADY_APPLIED, d, c);
    }
    public static TransferOutcome insufficient(BalanceSnapshot d) {
        return new TransferOutcome(Kind.INSUFFICIENT_BALANCE, d, null);
    }
    public boolean isSuccess() { return kind == Kind.APPLIED || kind == Kind.ALREADY_APPLIED; }
}
