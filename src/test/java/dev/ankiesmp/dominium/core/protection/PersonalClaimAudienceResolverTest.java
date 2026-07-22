package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.ClaimSettingsLookup;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersonalClaimAudienceResolverTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Claim personalClaim() {
        return new Claim(UUID.randomUUID(),
                new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), Instant.now());
    }

    // AR-001
    @Test
    void ownerBeatsTrustedBeatsVisitorBeatsPublic() {
        Claim c = personalClaim();
        var resolver = new PersonalClaimAudienceResolver((claim, p) ->
                p.equals(trusted) ? Optional.of(AccessLevel.TRUSTED)
                        : p.equals(visitor) ? Optional.of(AccessLevel.VISITOR)
                        : Optional.empty());
        assertEquals(Audience.PERSONAL_OWNER, resolver.resolve(c, owner));
        assertEquals(Audience.TRUSTED_PLAYER, resolver.resolve(c, trusted));
        assertEquals(Audience.PERSONAL_VISITOR, resolver.resolve(c, visitor));
        assertEquals(Audience.PUBLIC, resolver.resolve(c, stranger));
        assertEquals(Audience.PUBLIC, resolver.resolve(c, null));
    }

    // AR-002 — ProtectionService end-to-end voor trusted / visitor / public.
    @Test
    void protectionEndToEndAccessLevels() {
        Claim c = personalClaim();
        ClaimIndex idx = new ClaimIndex();
        idx.add(c);
        var resolver = new PersonalClaimAudienceResolver((claim, p) ->
                p.equals(trusted) ? Optional.of(AccessLevel.TRUSTED)
                        : p.equals(visitor) ? Optional.of(AccessLevel.VISITOR)
                        : Optional.empty());
        var protection = new ProtectionService(idx, resolver, FlagDefaults.standard(),
                ClaimSettingsLookup.NEVER);

        // Trusted mag build + container.
        assertTrue(protection.checkInClaim(c, trusted, Flag.BUILD).allowed());
        assertTrue(protection.checkInClaim(c, trusted, Flag.CONTAINER).allowed());
        // Visitor mag entry en door, maar geen container/build/bucket.
        assertTrue(protection.checkInClaim(c, visitor, Flag.ENTRY).allowed());
        assertTrue(protection.checkInClaim(c, visitor, Flag.DOOR).allowed());
        assertFalse(protection.checkInClaim(c, visitor, Flag.CONTAINER).allowed());
        assertFalse(protection.checkInClaim(c, visitor, Flag.BUILD).allowed());
        assertFalse(protection.checkInClaim(c, visitor, Flag.BUCKET).allowed());
        // Public mag alleen entry, geen container/build.
        assertTrue(protection.checkInClaim(c, stranger, Flag.ENTRY).allowed());
        assertFalse(protection.checkInClaim(c, stranger, Flag.CONTAINER).allowed());
    }

    // AR-003 — No Access: PUBLIC krijgt deny op alles inclusief ENTRY;
    //          trusted en owner blijven ongewijzigd.
    @Test
    void noAccessDeniesPublicEntryAndKeepsOwnersRights() {
        Claim c = personalClaim();
        ClaimIndex idx = new ClaimIndex();
        idx.add(c);
        var resolver = new PersonalClaimAudienceResolver((claim, p) ->
                p.equals(trusted) ? Optional.of(AccessLevel.TRUSTED)
                        : p.equals(visitor) ? Optional.of(AccessLevel.VISITOR)
                        : Optional.empty());
        ClaimSettingsLookup na = id -> true;
        var protection = new ProtectionService(idx, resolver, FlagDefaults.standard(), na);

        assertFalse(protection.checkInClaim(c, stranger, Flag.ENTRY).allowed(),
                "public krijgt no-access deny");
        assertFalse(protection.checkInClaim(c, stranger, Flag.DOOR).allowed());
        assertTrue(protection.checkInClaim(c, owner, Flag.BUILD).allowed());
        assertTrue(protection.checkInClaim(c, trusted, Flag.BUILD).allowed());
        // Visitor blijft entry houden (staat op de lijst).
        assertTrue(protection.checkInClaim(c, visitor, Flag.ENTRY).allowed());
    }
}
