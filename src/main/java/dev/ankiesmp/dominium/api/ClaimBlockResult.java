package dev.ankiesmp.dominium.api;

/**
 * Resultaat van een claim-block-mutatie. Gebruik de statische factories
 * om het juiste resultaattype op te bouwen.
 */
public final class ClaimBlockResult {

    public enum Status {
        APPLIED,
        ALREADY_APPLIED,
        INSUFFICIENT_BALANCE,
        INVALID_AMOUNT,
        HOLDER_UNKNOWN,
        ERROR
    }

    private final Status status;
    private final long newBalance;
    private final String message;

    private ClaimBlockResult(Status status, long newBalance, String message) {
        this.status = status;
        this.newBalance = newBalance;
        this.message = message;
    }

    public static ClaimBlockResult applied(long newBalance) {
        return new ClaimBlockResult(Status.APPLIED, newBalance, null);
    }

    public static ClaimBlockResult alreadyApplied(long currentBalance) {
        return new ClaimBlockResult(Status.ALREADY_APPLIED, currentBalance, null);
    }

    public static ClaimBlockResult insufficientBalance(long currentBalance) {
        return new ClaimBlockResult(Status.INSUFFICIENT_BALANCE, currentBalance, null);
    }

    public static ClaimBlockResult invalidAmount(String message) {
        return new ClaimBlockResult(Status.INVALID_AMOUNT, 0L, message);
    }

    public static ClaimBlockResult error(String message) {
        return new ClaimBlockResult(Status.ERROR, 0L, message);
    }

    public Status status() {
        return status;
    }

    public boolean isSuccess() {
        return status == Status.APPLIED || status == Status.ALREADY_APPLIED;
    }

    public long newBalance() {
        return newBalance;
    }

    public String message() {
        return message;
    }

    @Override
    public String toString() {
        return "ClaimBlockResult[" + status + ", balance=" + newBalance
                + (message == null ? "" : ", msg=" + message) + ']';
    }
}
