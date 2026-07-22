package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.kingdom.KingdomLookup;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie voor de MT-006 particle-bug: eigenaars moeten hun eigen border
 * kunnen zien met de nieuwe default {@code only-foreign-claims: false}.
 */
class ClaimBorderParticleDefaultTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID());
    private final KingdomLookup empty = new KingdomLookup() {
        @Override public Optional<KingdomMember> membershipFor(UUID p) { return Optional.empty(); }
        @Override public boolean isVisitor(UUID k, UUID p) { return false; }
    };

    // MT006-A — onlyForeign=false: eigen personal claim wordt geselecteerd.
    @Test
    void onlyForeignFalseIncludesOwnClaim() {
        UUID me = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(me), Instant.now()));
        var selector = new ClaimBorderSelector(idx, empty);
        var list = selector.select(world, -2, 5, me, 10.0, false);
        assertEquals(1, list.size(), "eigen claim moet zichtbaar zijn met onlyForeign=false");
    }

    // MT006-B — onlyForeign=true (oud gedrag) filtert de eigen claim (regressie-anker).
    @Test
    void onlyForeignTrueFiltersOwnClaim() {
        UUID me = UUID.randomUUID();
        var idx = new ClaimIndex();
        idx.add(new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(me), Instant.now()));
        var selector = new ClaimBorderSelector(idx, empty);
        assertTrue(selector.select(world, -2, 5, me, 10.0, true).isEmpty());
    }
}
