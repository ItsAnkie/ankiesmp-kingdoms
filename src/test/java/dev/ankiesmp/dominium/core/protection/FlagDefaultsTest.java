package dev.ankiesmp.dominium.core.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlagDefaultsTest {

    private final FlagDefaults defaults = FlagDefaults.standard();

    @Test
    void personalOwnerAllowsEverything() {
        for (Flag f : Flag.values()) {
            assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_OWNER, f),
                    () -> "owner should be allowed for " + f);
        }
    }

    @Test
    void visitorNeverGetsContainer() {
        assertEquals(Decision.DENY, defaults.get(Audience.KINGDOM_VISITOR, Flag.CONTAINER));
        assertEquals(Decision.DENY, defaults.get(Audience.KINGDOM_VISITOR, Flag.HOPPER_BORDER_TRANSFER));
        assertEquals(Decision.DENY, defaults.get(Audience.KINGDOM_VISITOR, Flag.BUILD));
        assertEquals(Decision.DENY, defaults.get(Audience.KINGDOM_VISITOR, Flag.BREAK));
    }

    @Test
    void visitorAllowsDoorsAndEntry() {
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.ENTRY));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.DOOR));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.TRAPDOOR));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.GATE));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.BUTTON));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_VISITOR, Flag.LEVER));
    }

    @Test
    void publicHasOnlyEntry() {
        assertEquals(Decision.ALLOW, defaults.get(Audience.PUBLIC, Flag.ENTRY));
        assertEquals(Decision.PASS, defaults.get(Audience.PUBLIC, Flag.BUILD));
        assertEquals(Decision.PASS, defaults.get(Audience.PUBLIC, Flag.CONTAINER));
    }

    @Test
    void allyHasEntryButNoBuildOrContainer() {
        assertEquals(Decision.ALLOW, defaults.get(Audience.ALLY, Flag.ENTRY));
        assertEquals(Decision.DENY, defaults.get(Audience.ALLY, Flag.BUILD));
        assertEquals(Decision.DENY, defaults.get(Audience.ALLY, Flag.CONTAINER));
        assertEquals(Decision.DENY, defaults.get(Audience.ALLY, Flag.PVP));
    }

    @Test
    void kingdomMemberCanBuildAndContainer() {
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_MEMBER, Flag.BUILD));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_MEMBER, Flag.BREAK));
        assertEquals(Decision.ALLOW, defaults.get(Audience.KINGDOM_MEMBER, Flag.CONTAINER));
    }

    @Test
    void personalVisitorMatchesInvariants() {
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.ENTRY));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.DOOR));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.TRAPDOOR));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.GATE));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.BUTTON));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.LEVER));
        assertEquals(Decision.ALLOW, defaults.get(Audience.PERSONAL_VISITOR, Flag.PRESSURE_PLATE));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.CONTAINER));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.BUILD));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.BREAK));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.BUCKET));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.REDSTONE_INTERACT));
        assertEquals(Decision.DENY,  defaults.get(Audience.PERSONAL_VISITOR, Flag.PVP));
    }
}
