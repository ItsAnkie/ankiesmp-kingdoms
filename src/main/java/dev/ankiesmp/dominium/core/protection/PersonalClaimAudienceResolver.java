package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.AccessLookup;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fase 3 audience resolver voor persoonlijke claims.
 *
 * <p>Precedence:
 * <ol>
 *   <li>owner → PERSONAL_OWNER;</li>
 *   <li>trusted → TRUSTED_PLAYER;</li>
 *   <li>visitor → PERSONAL_VISITOR;</li>
 *   <li>else → PUBLIC.</li>
 * </ol>
 *
 * <p>Fase 4 vervangt/decoreert dit met kingdom-audiences (LEADER,
 * CO_LEADER, MEMBER, KINGDOM_VISITOR, ALLY) — de interface blijft
 * gelijk zodat fase 3-listeners niet opnieuw ontworpen hoeven te worden.
 *
 * <p><b>Hot path:</b> {@link AccessLookup} moet non-blocking zijn.
 * De productie-implementatie leest uit de {@code TerritoryContextCache}.
 */
public final class PersonalClaimAudienceResolver implements AudienceResolver {

    private final AccessLookup accessLookup;

    public PersonalClaimAudienceResolver(AccessLookup accessLookup) {
        this.accessLookup = Objects.requireNonNull(accessLookup, "accessLookup");
    }

    @Override
    public Audience resolve(Claim claim, UUID actorId) {
        Objects.requireNonNull(claim, "claim");
        if (actorId == null) return Audience.PUBLIC;
        if (claim.owner().type() == ClaimType.PERSONAL && claim.owner().id().equals(actorId)) {
            return Audience.PERSONAL_OWNER;
        }
        if (claim.owner().type() != ClaimType.PERSONAL) {
            // Kingdom/admin claims: fase 4 breidt dit uit. Voor nu default PUBLIC.
            return Audience.PUBLIC;
        }
        Optional<AccessLevel> level = accessLookup.levelFor(claim.id(), actorId);
        if (level.isEmpty()) return Audience.PUBLIC;
        return switch (level.get()) {
            case TRUSTED -> Audience.TRUSTED_PLAYER;
            case VISITOR -> Audience.PERSONAL_VISITOR;
        };
    }
}
