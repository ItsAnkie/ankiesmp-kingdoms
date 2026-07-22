package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.kingdom.KingdomLookup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Selecteert de claims waarvan de border op een gegeven positie voor een
 * speler getekend moet worden. Pure — gebruikt de bestaande
 * {@link ClaimIndex} voor spatial lookup en {@link KingdomLookup} voor
 * "own-claim"-detectie bij kingdomterritory.
 *
 * <p>{@code onlyForeign=true}: filtert claims die de speler zelf bezit
 * (persoonlijk) of waarvan hij lid is (kingdom).
 */
public final class ClaimBorderSelector {

    private final ClaimIndex index;
    private final KingdomLookup kingdomLookup;

    public ClaimBorderSelector(ClaimIndex index, KingdomLookup kingdomLookup) {
        this.index = Objects.requireNonNull(index);
        this.kingdomLookup = Objects.requireNonNull(kingdomLookup);
    }

    public List<Claim> select(WorldRef world, double px, double pz, UUID playerId,
                              double triggerDistance, boolean onlyForeign) {
        int r = (int) Math.ceil(triggerDistance);
        int minX = (int) Math.floor(px) - r;
        int maxX = (int) Math.floor(px) + r;
        int minZ = (int) Math.floor(pz) - r;
        int maxZ = (int) Math.floor(pz) + r;

        Set<UUID> seen = new HashSet<>();
        List<Claim> out = new ArrayList<>();
        // ClaimIndex.overlapping werkt met chunk-buckets voor een rechthoek.
        for (Claim c : index.overlapping(world,
                dev.ankiesmp.dominium.core.claim.ClaimRectangle.ofCorners(minX, minZ, maxX, maxZ))) {
            if (!seen.add(c.id())) continue;
            if (onlyForeign && isOwnTerritory(c, playerId)) continue;
            if (!ClaimBorderGeometry.inTriggerRange(c.rect(), px, pz, triggerDistance)) continue;
            out.add(c);
        }
        return out;
    }

    public boolean isOwnTerritory(Claim c, UUID playerId) {
        if (c.owner().type() == ClaimType.PERSONAL) {
            return c.owner().id().equals(playerId);
        }
        if (c.owner().type() == ClaimType.KINGDOM) {
            Optional<dev.ankiesmp.dominium.core.kingdom.KingdomMember> m =
                    kingdomLookup.membershipFor(playerId);
            return m.isPresent() && m.get().kingdomId().equals(c.owner().id());
        }
        return false;
    }
}
