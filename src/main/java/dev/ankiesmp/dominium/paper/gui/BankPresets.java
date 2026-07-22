package dev.ankiesmp.dominium.paper.gui;

/**
 * Statische mapping van bank-preset actions op hun bedrag/aantal.
 * Bedragen zijn in <b>major units</b> voor deposit/withdraw (Vault) en
 * in <b>blocks</b> voor pool-contribute/-buy. Presets zijn bewust vast
 * zodat een malicious tampering van GUI-items het bedrag niet kan
 * beïnvloeden — de service leest hier op basis van de action-enum,
 * niet uit item-data.
 */
final class BankPresets {

    private BankPresets() {}

    /** Vault major-units per DEPOSIT/WITHDRAW-actie. Wordt vermenigvuldigd
     *  met minor-per-major (100) tot amountMinor. */
    static long depositMajor(KingdomGuiAction action) {
        return switch (action) {
            case BANK_DEPOSIT_10   -> 10L;
            case BANK_DEPOSIT_100  -> 100L;
            case BANK_DEPOSIT_1000 -> 1000L;
            default -> 0L;
        };
    }
    static long withdrawMajor(KingdomGuiAction action) {
        return switch (action) {
            case BANK_WITHDRAW_10   -> 10L;
            case BANK_WITHDRAW_100  -> 100L;
            case BANK_WITHDRAW_1000 -> 1000L;
            default -> 0L;
        };
    }
    /** Blocks per CONTRIBUTE/BUY-actie. */
    static long contributeBlocks(KingdomGuiAction action) {
        return switch (action) {
            case POOL_CONTRIBUTE_10   -> 10L;
            case POOL_CONTRIBUTE_100  -> 100L;
            case POOL_CONTRIBUTE_1000 -> 1000L;
            default -> 0L;
        };
    }
    static long buyBlocks(KingdomGuiAction action) {
        return switch (action) {
            case POOL_BUY_10   -> 10L;
            case POOL_BUY_100  -> 100L;
            case POOL_BUY_1000 -> 1000L;
            default -> 0L;
        };
    }
    /** Vault-minor = major * 100 (2 decimalen — standaard Vault-conventie). */
    static long minorUnits(long major) { return major * 100L; }
}
