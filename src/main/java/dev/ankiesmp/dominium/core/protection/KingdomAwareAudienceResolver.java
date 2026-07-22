package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.kingdom.KingdomLookup;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomRole;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fase 4 resolver. Delegeert persoonlijke claims naar
 * {@link PersonalClaimAudienceResolver}; voor kingdomclaims bepaalt hij
 * zelf de audience via {@link KingdomLookup}.
 *
 * <p>Precedence voor kingdomclaims:
 * <ol>
 *   <li>KINGDOM_LEADER (owner is dit kingdom, actor is de LEADER)</li>
 *   <li>KINGDOM_CO_LEADER</li>
 *   <li>KINGDOM_MEMBER</li>
 *   <li>KINGDOM_VISITOR (aparte visitorlijst)</li>
 *   <li>PUBLIC (fase 6 kan hier ALLY tussen zetten)</li>
 * </ol>
 */
public final class KingdomAwareAudienceResolver implements AudienceResolver {

    private final AudienceResolver personal;
    private final KingdomLookup kingdomLookup;

    public KingdomAwareAudienceResolver(AudienceResolver personal, KingdomLookup kingdomLookup) {
        this.personal = Objects.requireNonNull(personal);
        this.kingdomLookup = Objects.requireNonNull(kingdomLookup);
    }

    @Override
    public Audience resolve(Claim claim, UUID actorId) {
        Objects.requireNonNull(claim);
        if (claim.owner().type() != ClaimType.KINGDOM) {
            return personal.resolve(claim, actorId);
        }
        if (actorId == null) return Audience.PUBLIC;
        UUID owningKingdom = claim.owner().id();
        Optional<KingdomMember> membership = kingdomLookup.membershipFor(actorId);
        if (membership.isPresent() && membership.get().kingdomId().equals(owningKingdom)) {
            return switch (membership.get().role()) {
                case LEADER -> Audience.KINGDOM_LEADER;
                case CO_LEADER -> Audience.KINGDOM_CO_LEADER;
                case MEMBER -> Audience.KINGDOM_MEMBER;
            };
        }
        if (kingdomLookup.isVisitor(owningKingdom, actorId)) {
            return Audience.KINGDOM_VISITOR;
        }
        // Fase 6 zal hier ALLY tussen zetten. Nu default naar PUBLIC.
        return Audience.PUBLIC;
    }
}
