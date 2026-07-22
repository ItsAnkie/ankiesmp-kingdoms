package dev.ankiesmp.dominium.core.kingdom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KingdomPermissionServiceTest {

    @Test
    void leaderCanDoEverything() {
        for (var a : KingdomPermissionService.Action.values()) {
            assertTrue(KingdomPermissionService.allowed(KingdomRole.LEADER, a));
        }
    }

    @Test
    void coLeaderCanInviteAndManageVisitorsAndClaims() {
        assertTrue(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.INVITE));
        assertTrue(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.MANAGE_VISITORS));
        assertTrue(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.MANAGE_KINGDOM_CLAIMS));
        assertTrue(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.KICK_MEMBER));
    }

    @Test
    void coLeaderCannotAffectLeaderOrOtherCoLeaders() {
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.KICK_CO_LEADER));
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.DEMOTE_CO_LEADER));
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.PROMOTE_TO_CO_LEADER));
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.TRANSFER_LEADERSHIP));
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.DISBAND));
        assertFalse(KingdomPermissionService.allowed(KingdomRole.CO_LEADER,
                KingdomPermissionService.Action.MANAGE_KINGDOM_BANK));
    }

    @Test
    void memberHasNoAdministrativePower() {
        for (var a : KingdomPermissionService.Action.values()) {
            assertFalse(KingdomPermissionService.allowed(KingdomRole.MEMBER, a),
                    () -> "member should NOT be allowed to " + a);
        }
    }
}
