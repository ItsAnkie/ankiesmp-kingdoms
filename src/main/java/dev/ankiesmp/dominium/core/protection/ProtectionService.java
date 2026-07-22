package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.access.ClaimSettingsLookup;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;

import java.util.Objects;
import java.util.UUID;

/**
 * Hot-path entry voor listeners. Beantwoordt "mag {@code actor} op deze
 * plek {@code flag} uitvoeren?". De implementatie is bewust
 * allocation-arm: wilderness returned een gedeelde constante en de
 * gemeenschappelijke pad-lookup doet één bucket-hit.
 *
 * <p>Volgorde per master-prompt §10.1:
 * <ol>
 *     <li>wilderness → ALLOW (geen bescherming);</li>
 *     <li>audience-specifieke override (fase &gt;2, nog niet gebruikt);</li>
 *     <li>audience-specifieke default uit {@link FlagDefaults};</li>
 *     <li>PUBLIC-default;</li>
 *     <li>veilige deny-default.</li>
 * </ol>
 * Bij gelijke specificiteit wint deny.
 */
public final class ProtectionService {

    private final ClaimIndex index;
    private final AudienceResolver audienceResolver;
    private final FlagDefaults defaults;
    private final ClaimSettingsLookup settings;

    public ProtectionService(ClaimIndex index,
                             AudienceResolver audienceResolver,
                             FlagDefaults defaults) {
        this(index, audienceResolver, defaults, ClaimSettingsLookup.NEVER);
    }

    public ProtectionService(ClaimIndex index,
                             AudienceResolver audienceResolver,
                             FlagDefaults defaults,
                             ClaimSettingsLookup settings) {
        this.index = Objects.requireNonNull(index, "index");
        this.audienceResolver = Objects.requireNonNull(audienceResolver, "audienceResolver");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public AccessDecision check(WorldRef world, int x, int z, UUID actorId, Flag flag) {
        Claim claim = index.containing(world, x, z).orElse(null);
        if (claim == null) return AccessDecision.wilderness();
        return checkInClaim(claim, actorId, flag);
    }

    /** Convenience wanneer de caller al weet in welke claim we zitten. */
    public AccessDecision checkInClaim(Claim claim, UUID actorId, Flag flag) {
        Audience audience = audienceResolver.resolve(claim, actorId);

        // No Access-overlay op persoonlijke claims: PUBLIC krijgt hard deny op alle
        // flags (dus ook ENTRY). Owner / trusted / expliciete visitor blijven ongewijzigd.
        if (audience == Audience.PUBLIC
                && claim.owner().type() == ClaimType.PERSONAL
                && settings.noAccess(claim.id())) {
            return deny(claim, audience, "no-access");
        }

        Decision own = defaults.get(audience, flag);
        Decision pub = audience == Audience.PUBLIC
                ? Decision.PASS
                : defaults.get(Audience.PUBLIC, flag);

        // Deny-wins bij gelijke specificiteit betekent: als de eigen audience
        // en de public-fallback met dezelfde flag beide iets zeggen, wint DENY.
        if (own == Decision.DENY) return deny(claim, audience, "audience");
        if (pub == Decision.DENY && own == Decision.PASS) return deny(claim, audience, "public");
        if (own == Decision.ALLOW) return allow(claim, audience, "audience");
        if (pub == Decision.ALLOW) return allow(claim, audience, "public");
        return deny(claim, audience, "safe-default");
    }

    private static AccessDecision allow(Claim claim, Audience audience, String reason) {
        return AccessDecision.inClaim(claim, audience, true, reason);
    }

    private static AccessDecision deny(Claim claim, Audience audience, String reason) {
        return AccessDecision.inClaim(claim, audience, false, reason);
    }
}
