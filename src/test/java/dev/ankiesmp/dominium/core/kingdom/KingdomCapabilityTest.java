package dev.ankiesmp.dominium.core.kingdom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KingdomCapabilityTest {

    @Test
    void viewAndDepositAllowedForAllMembers() {
        for (KingdomRole r : KingdomRole.values()) {
            assertTrue(KingdomCapability.allowed(r, KingdomCapability.VIEW_BANK));
            assertTrue(KingdomCapability.allowed(r, KingdomCapability.DEPOSIT_BANK));
            assertTrue(KingdomCapability.allowed(r, KingdomCapability.CONTRIBUTE_CLAIMBLOCKS));
        }
    }

    @Test
    void withdrawAndBuyOnlyForLeaderCoLeader() {
        assertTrue(KingdomCapability.allowed(KingdomRole.LEADER, KingdomCapability.WITHDRAW_BANK));
        assertTrue(KingdomCapability.allowed(KingdomRole.CO_LEADER, KingdomCapability.WITHDRAW_BANK));
        assertFalse(KingdomCapability.allowed(KingdomRole.MEMBER, KingdomCapability.WITHDRAW_BANK));

        assertTrue(KingdomCapability.allowed(KingdomRole.LEADER, KingdomCapability.BUY_CLAIMBLOCKS));
        assertTrue(KingdomCapability.allowed(KingdomRole.CO_LEADER, KingdomCapability.BUY_CLAIMBLOCKS));
        assertFalse(KingdomCapability.allowed(KingdomRole.MEMBER, KingdomCapability.BUY_CLAIMBLOCKS));
    }
}
