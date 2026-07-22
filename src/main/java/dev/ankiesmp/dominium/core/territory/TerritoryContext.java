package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.protection.Audience;

import java.util.Objects;
import java.util.Optional;

/**
 * Wat een speler op deze block-positie ziet: wilderness of een claim
 * plus de audience van de speler in die claim.
 */
public record TerritoryContext(Optional<Claim> claim, Audience audience, boolean noAccess) {
    public TerritoryContext {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(audience);
    }
    public static TerritoryContext wilderness() {
        return new TerritoryContext(Optional.empty(), Audience.PUBLIC, false);
    }
    public boolean inClaim() { return claim.isPresent(); }
}
