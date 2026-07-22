package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionServiceTest {

    private ClaimIndex index;
    private WorldRef world;
    private UUID owner;
    private UUID stranger;
    private ProtectionService protection;
    private Claim claim;

    @BeforeEach
    void setUp() {
        index = new ClaimIndex();
        world = new WorldRef(UUID.randomUUID());
        owner = UUID.randomUUID();
        stranger = UUID.randomUUID();
        claim = new Claim(UUID.randomUUID(), world,
                new ClaimRectangle(0, 0, 15, 15),
                ClaimOwner.personal(owner),
                Instant.now());
        index.add(claim);

        AudienceResolver resolver = (c, actor) -> {
            if (actor != null && c.owner().id().equals(actor)) return Audience.PERSONAL_OWNER;
            return Audience.PUBLIC;
        };
        protection = new ProtectionService(index, resolver, FlagDefaults.standard());
    }

    @Test
    void wildernessAlwaysAllowedRegardlessOfActor() {
        AccessDecision d = protection.check(world, 100, 100, stranger, Flag.BUILD);
        assertTrue(d.allowed());
        assertEquals(Audience.PUBLIC, d.audience());
        assertEquals("wilderness", d.reason());
    }

    @Test
    void ownerCanBuildInsideOwnClaim() {
        AccessDecision d = protection.check(world, 5, 5, owner, Flag.BUILD);
        assertTrue(d.allowed());
        assertEquals(Audience.PERSONAL_OWNER, d.audience());
    }

    @Test
    void strangerCannotBuildInsideClaim() {
        AccessDecision d = protection.check(world, 5, 5, stranger, Flag.BUILD);
        assertFalse(d.allowed());
        assertEquals(Audience.PUBLIC, d.audience());
    }

    @Test
    void strangerMayEnterButNotOpenContainer() {
        assertTrue(protection.check(world, 5, 5, stranger, Flag.ENTRY).allowed());
        assertFalse(protection.check(world, 5, 5, stranger, Flag.CONTAINER).allowed());
    }

    @Test
    void anonymousActorFallsThroughToPublic() {
        AccessDecision d = protection.check(world, 5, 5, null, Flag.BUILD);
        assertFalse(d.allowed());
        assertEquals(Audience.PUBLIC, d.audience());
    }

    @Test
    void denyWinsOverPublicAllow() {
        // A synthetic resolver that puts the actor in KINGDOM_VISITOR (deny container)
        // even though PUBLIC would only PASS. Expected: deny.
        AudienceResolver visitorResolver = (c, actor) -> Audience.KINGDOM_VISITOR;
        ProtectionService svc = new ProtectionService(index, visitorResolver, FlagDefaults.standard());
        AccessDecision d = svc.check(world, 5, 5, stranger, Flag.CONTAINER);
        assertFalse(d.allowed(), "visitor deny should stand");
        assertEquals(Audience.KINGDOM_VISITOR, d.audience());
    }

    @Test
    void safeDefaultDeniesUnspecifiedFlag() {
        // ALLY has entry allow, but MOB_GRIEFING is unspecified → safe deny.
        AudienceResolver allyResolver = (c, actor) -> Audience.ALLY;
        ProtectionService svc = new ProtectionService(index, allyResolver, FlagDefaults.standard());
        AccessDecision d = svc.check(world, 5, 5, stranger, Flag.MOB_GRIEFING);
        assertFalse(d.allowed());
        assertEquals("safe-default", d.reason());
    }
}
