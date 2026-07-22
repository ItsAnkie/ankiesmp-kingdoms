package dev.ankiesmp.dominium.paper.protection;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.protection.Audience;
import dev.ankiesmp.dominium.core.protection.AudienceResolver;

import java.util.UUID;

/**
 * Fase 2 {@link AudienceResolver}: kent alleen "PERSONAL_OWNER" en
 * "PUBLIC". Zodra fase 4 kingdoms/members introduceert wordt deze
 * resolver vervangen door een uitgebreidere variant, zonder dat
 * {@code ProtectionService} of listeners hoeven te veranderen.
 */
public final class PersonalOwnerAudienceResolver implements AudienceResolver {

    @Override
    public Audience resolve(Claim claim, UUID actorId) {
        if (claim.owner().type() == ClaimType.PERSONAL
                && actorId != null
                && claim.owner().id().equals(actorId)) {
            return Audience.PERSONAL_OWNER;
        }
        return Audience.PUBLIC;
    }
}
