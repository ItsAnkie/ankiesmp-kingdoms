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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie voor de "eigenaar ziet zijn eigen border niet"-bug: dit mag
 * alleen gebeuren wanneer onlyForeign=true.
 */
class OwnBorderVisibilityTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID());

    private ClaimBorderSelector selectorWith(ClaimIndex idx, KingdomLookup lookup) {
        return new ClaimBorderSelector(idx, lookup);
    }

    private KingdomLookup empty() {
        return new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID p) { return Optional.empty(); }
            @Override public boolean isVisitor(UUID k, UUID p) { return false; }
        };
    }

    // OB-001 — persoonlijke owner + onlyForeign=false → claim geselecteerd.
    @Test
    void ownerSeesOwnBorderWhenOnlyForeignFalse() {
        UUID me = UUID.randomUUID();
        var idx = new ClaimIndex();
        var c = new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(me), Instant.now());
        idx.add(c);
        var sel = selectorWith(idx, empty());
        var picked = sel.select(world, -2, 5, me, 10.0, false);
        assertEquals(1, picked.size(), "owner met onlyForeign=false MOET eigen claim zien");
    }

    // OB-002 — persoonlijke owner + onlyForeign=true → claim overgeslagen.
    @Test
    void ownerSkippedWhenOnlyForeignTrue() {
        UUID me = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(me), Instant.now()));
        var sel = selectorWith(idx, empty());
        assertTrue(sel.select(world, -2, 5, me, 10.0, true).isEmpty());
    }

    // OB-003 — vreemde claim → altijd geselecteerd, ongeacht onlyForeign.
    @Test
    void foreignClaimAlwaysSelectedInRange() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(other), Instant.now()));
        var sel = selectorWith(idx, empty());
        assertEquals(1, sel.select(world, -2, 5, me, 10.0, false).size());
        assertEquals(1, sel.select(world, -2, 5, me, 10.0, true).size());
    }

    // OB-004 — kingdom LEADER + onlyForeign=false → eigen kingdomclaim zichtbaar.
    @Test
    void kingdomLeaderSeesOwnBorder() {
        UUID kingdomId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(kingdomId), Instant.now()));
        KingdomLookup lookup = new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID p) {
                return p.equals(leader)
                        ? Optional.of(new KingdomMember(kingdomId, p, KingdomRole.LEADER,
                                Instant.EPOCH, Optional.empty()))
                        : Optional.empty();
            }
            @Override public boolean isVisitor(UUID k, UUID p) { return false; }
        };
        var sel = selectorWith(idx, lookup);
        assertEquals(1, sel.select(world, -2, 5, leader, 10.0, false).size(),
                "leader met onlyForeign=false MOET eigen kingdomclaim zien");
        assertTrue(sel.select(world, -2, 5, leader, 10.0, true).isEmpty(),
                "leader met onlyForeign=true wordt gefilterd");
    }

    // OB-005 — kingdom MEMBER + onlyForeign=false → eigen kingdomclaim zichtbaar.
    @Test
    void kingdomMemberSeesOwnBorder() {
        UUID kingdomId = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.kingdom(kingdomId), Instant.now()));
        KingdomLookup lookup = new KingdomLookup() {
            @Override public Optional<KingdomMember> membershipFor(UUID p) {
                return p.equals(member)
                        ? Optional.of(new KingdomMember(kingdomId, p, KingdomRole.MEMBER,
                                Instant.EPOCH, Optional.empty()))
                        : Optional.empty();
            }
            @Override public boolean isVisitor(UUID k, UUID p) { return false; }
        };
        var sel = selectorWith(idx, lookup);
        assertEquals(1, sel.select(world, -2, 5, member, 10.0, false).size());
    }
}
