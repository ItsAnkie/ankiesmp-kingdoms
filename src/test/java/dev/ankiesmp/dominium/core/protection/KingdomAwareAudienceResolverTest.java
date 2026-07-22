package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.kingdom.KingdomLookup;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KingdomAwareAudienceResolverTest {

    private final UUID kingdomId = UUID.randomUUID();
    private final UUID leader = UUID.randomUUID();
    private final UUID coleader = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private Claim kingdomClaim() {
        return new Claim(UUID.randomUUID(), new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(kingdomId), Instant.now());
    }

    private KingdomLookup lookup() {
        return new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID p) {
                if (p.equals(leader)) return Optional.of(new KingdomMember(kingdomId, p, KingdomRole.LEADER,
                        Instant.EPOCH, Optional.empty()));
                if (p.equals(coleader)) return Optional.of(new KingdomMember(kingdomId, p, KingdomRole.CO_LEADER,
                        Instant.EPOCH, Optional.empty()));
                if (p.equals(member)) return Optional.of(new KingdomMember(kingdomId, p, KingdomRole.MEMBER,
                        Instant.EPOCH, Optional.empty()));
                return Optional.empty();
            }
            @Override public boolean isVisitor(UUID kid, UUID p) {
                return kid.equals(kingdomId) && p.equals(visitor);
            }
        };
    }

    // KA-001
    @Test
    void precedenceLeaderCoLeaderMemberVisitorPublic() {
        var resolver = new KingdomAwareAudienceResolver(
                new PersonalClaimAudienceResolver((c, p) -> Optional.empty()),
                lookup());
        Claim c = kingdomClaim();
        assertEquals(Audience.KINGDOM_LEADER, resolver.resolve(c, leader));
        assertEquals(Audience.KINGDOM_CO_LEADER, resolver.resolve(c, coleader));
        assertEquals(Audience.KINGDOM_MEMBER, resolver.resolve(c, member));
        assertEquals(Audience.KINGDOM_VISITOR, resolver.resolve(c, visitor));
        assertEquals(Audience.PUBLIC, resolver.resolve(c, stranger));
        assertEquals(Audience.PUBLIC, resolver.resolve(c, null));
    }

    // KA-002 — member van een ander kingdom valt naar PUBLIC (of visitor).
    @Test
    void memberOfDifferentKingdomIsPublic() {
        UUID otherKingdom = UUID.randomUUID();
        UUID p = UUID.randomUUID();
        KingdomLookup lookup = new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID id) {
                if (id.equals(p)) return Optional.of(new KingdomMember(otherKingdom, p,
                        KingdomRole.LEADER, Instant.EPOCH, Optional.empty()));
                return Optional.empty();
            }
            @Override public boolean isVisitor(UUID k, UUID id) { return false; }
        };
        var resolver = new KingdomAwareAudienceResolver(
                new PersonalClaimAudienceResolver((c, x) -> Optional.empty()), lookup);
        assertEquals(Audience.PUBLIC, resolver.resolve(kingdomClaim(), p));
    }

    // KA-003 — personal claim wordt naar de personal-resolver gedelegeerd.
    @Test
    void personalClaimsGoToPersonalResolver() {
        UUID owner = UUID.randomUUID();
        Claim personal = new Claim(UUID.randomUUID(), new WorldRef(UUID.randomUUID()),
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), Instant.now());
        var resolver = new KingdomAwareAudienceResolver(
                new PersonalClaimAudienceResolver((c, p) -> Optional.empty()), lookup());
        assertEquals(Audience.PERSONAL_OWNER, resolver.resolve(personal, owner));
        assertEquals(Audience.PUBLIC, resolver.resolve(personal, stranger));
    }
}
