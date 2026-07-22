package dev.ankiesmp.dominium.core.claim;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MutableClaimMutationGuardTest {

    // MG-101 — block/unblock werkt per (type, ownerId).
    @Test
    void blockAndUnblock() {
        var g = new MutableClaimMutationGuard();
        UUID player = UUID.randomUUID();
        g.block(ClaimType.PERSONAL, player);
        assertTrue(g.isBlocked(ClaimType.PERSONAL, player));
        assertTrue(g.unblock(ClaimType.PERSONAL, player));
        assertFalse(g.isBlocked(ClaimType.PERSONAL, player));
        assertFalse(g.unblock(ClaimType.PERSONAL, player));
    }

    // MG-102 — andere owner niet geraakt.
    @Test
    void unblockDoesNotAffectOtherOwners() {
        var g = new MutableClaimMutationGuard();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        g.block(ClaimType.PERSONAL, a);
        g.block(ClaimType.PERSONAL, b);
        g.unblock(ClaimType.PERSONAL, a);
        assertFalse(g.isBlocked(ClaimType.PERSONAL, a));
        assertTrue(g.isBlocked(ClaimType.PERSONAL, b));
    }

    // MG-103 — reason is user-friendly (bevat geen 'BLOCKED' interne enum).
    @Test
    void reasonIsUserFriendly() {
        var g = new MutableClaimMutationGuard();
        String r = g.reason(ClaimType.PERSONAL, UUID.randomUUID());
        assertFalse(r.contains("BLOCKED"), "reason mag geen interne enumnaam bevatten");
        assertTrue(r.toLowerCase().contains("legacy claims"),
                "reason bevat spelervriendelijke uitleg");
        assertTrue(r.toLowerCase().contains("administrator"),
                "verwijst naar administrator voor repair");
    }
}
