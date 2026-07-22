package dev.ankiesmp.dominium.paper.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bewijst dat preset-bedragen uitsluitend uit de action-enum komen; onbekende
 * of NOOP-actions geven 0 terug (defense-in-depth tegen tampering).
 */
class BankPresetsTest {

    @Test
    void depositPresetsAreExact() {
        assertEquals(10L, BankPresets.depositMajor(KingdomGuiAction.BANK_DEPOSIT_10));
        assertEquals(100L, BankPresets.depositMajor(KingdomGuiAction.BANK_DEPOSIT_100));
        assertEquals(1000L, BankPresets.depositMajor(KingdomGuiAction.BANK_DEPOSIT_1000));
    }

    @Test
    void withdrawPresetsAreExact() {
        assertEquals(10L, BankPresets.withdrawMajor(KingdomGuiAction.BANK_WITHDRAW_10));
        assertEquals(100L, BankPresets.withdrawMajor(KingdomGuiAction.BANK_WITHDRAW_100));
        assertEquals(1000L, BankPresets.withdrawMajor(KingdomGuiAction.BANK_WITHDRAW_1000));
    }

    @Test
    void contributePresetsAreExact() {
        assertEquals(10L, BankPresets.contributeBlocks(KingdomGuiAction.POOL_CONTRIBUTE_10));
        assertEquals(100L, BankPresets.contributeBlocks(KingdomGuiAction.POOL_CONTRIBUTE_100));
        assertEquals(1000L, BankPresets.contributeBlocks(KingdomGuiAction.POOL_CONTRIBUTE_1000));
    }

    @Test
    void buyPresetsAreExact() {
        assertEquals(10L, BankPresets.buyBlocks(KingdomGuiAction.POOL_BUY_10));
        assertEquals(100L, BankPresets.buyBlocks(KingdomGuiAction.POOL_BUY_100));
        assertEquals(1000L, BankPresets.buyBlocks(KingdomGuiAction.POOL_BUY_1000));
    }

    @Test
    void unrelatedActionsYieldZero() {
        for (KingdomGuiAction a : KingdomGuiAction.values()) {
            if (a.name().startsWith("BANK_DEPOSIT_")) continue;
            assertEquals(0L, BankPresets.depositMajor(a));
        }
        for (KingdomGuiAction a : KingdomGuiAction.values()) {
            if (a.name().startsWith("BANK_WITHDRAW_")) continue;
            assertEquals(0L, BankPresets.withdrawMajor(a));
        }
    }

    @Test
    void minorConversionIsHundredfold() {
        assertEquals(0L, BankPresets.minorUnits(0L));
        assertEquals(100L, BankPresets.minorUnits(1L));
        assertEquals(100_000L, BankPresets.minorUnits(1000L));
    }
}
