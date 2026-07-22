package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.kingdom.KingdomLookup;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClaimBorderSelectorTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID());
    private final KingdomLookup emptyLookup = new KingdomLookup() {
        @Override public Optional<KingdomMember> membershipFor(UUID p) { return Optional.empty(); }
        @Override public boolean isVisitor(UUID k, UUID p) { return false; }
    };

    private Claim personal(UUID owner, int minX, int minZ, int maxX, int maxZ) {
        return new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ),
                ClaimOwner.personal(owner), Instant.now());
    }

    private Claim kingdom(UUID kingdomId, int minX, int minZ, int maxX, int maxZ) {
        return new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ),
                ClaimOwner.kingdom(kingdomId), Instant.now());
    }

    // BS-001 — nearby foreign claim wordt geselecteerd.
    @Test
    void foreignClaimNearbyIsSelected() {
        UUID other = UUID.randomUUID(), me = UUID.randomUUID();
        var idx = new ClaimIndex();
        var c = personal(other, 0, 0, 9, 9);
        idx.add(c);
        var sel = new ClaimBorderSelector(idx, emptyLookup);
        List<Claim> pick = sel.select(world, -2, 5, me, 10.0, true);
        assertEquals(1, pick.size());
        assertEquals(c.id(), pick.get(0).id());
    }

    // BS-002 — own personal claim wordt gefilterd.
    @Test
    void ownPersonalClaimIsFilteredWhenOnlyForeign() {
        UUID me = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(personal(me, 0, 0, 9, 9));
        var sel = new ClaimBorderSelector(idx, emptyLookup);
        assertTrue(sel.select(world, -2, 5, me, 10.0, true).isEmpty());
        // Met onlyForeign=false verschijnt hij wel.
        assertEquals(1, sel.select(world, -2, 5, me, 10.0, false).size());
    }

    // BS-003 — kingdommembership telt als own-territory.
    @Test
    void kingdomMembershipCountsAsOwn() {
        UUID kingdomId = UUID.randomUUID(), me = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(kingdom(kingdomId, 0, 0, 9, 9));
        KingdomLookup lookup = new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID p) {
                if (p.equals(me)) return Optional.of(new KingdomMember(kingdomId, p,
                        KingdomRole.MEMBER, Instant.EPOCH, Optional.empty()));
                return Optional.empty();
            }
            @Override public boolean isVisitor(UUID k, UUID p) { return false; }
        };
        var sel = new ClaimBorderSelector(idx, lookup);
        assertTrue(sel.select(world, -2, 5, me, 10.0, true).isEmpty());
        assertEquals(1, sel.select(world, -2, 5, UUID.randomUUID(), 10.0, true).size(),
                "vreemdeling krijgt de border wel te zien");
    }

    // BS-004 — speler ver weg krijgt niets.
    @Test
    void farPlayerHasNoClaims() {
        UUID me = UUID.randomUUID(), other = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(personal(other, 0, 0, 9, 9));
        var sel = new ClaimBorderSelector(idx, emptyLookup);
        assertTrue(sel.select(world, 200, 200, me, 10.0, true).isEmpty());
    }
}
