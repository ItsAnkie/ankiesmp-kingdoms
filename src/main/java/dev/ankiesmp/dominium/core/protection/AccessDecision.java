package dev.ankiesmp.dominium.core.protection;

import dev.ankiesmp.dominium.core.claim.Claim;

import java.util.Objects;
import java.util.Optional;

/**
 * Effectieve uitkomst van {@code ProtectionService#check}.
 *
 * <p>{@link #claim} is leeg bij wilderness — de meeste listeners moeten
 * daar meteen returnen. Bij een niet-lege claim geeft {@link #audience}
 * aan waar de actor uiteindelijk in de precedence-stack terechtkwam,
 * plus of het uiteindelijke antwoord ALLOW of DENY is.
 */
public final class AccessDecision {

    private static final AccessDecision WILDERNESS_ALLOW =
            new AccessDecision(null, Audience.PUBLIC, true, "wilderness");

    private final Claim claim;
    private final Audience audience;
    private final boolean allowed;
    private final String reason;

    private AccessDecision(Claim claim, Audience audience, boolean allowed, String reason) {
        this.claim = claim;
        this.audience = audience;
        this.allowed = allowed;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public static AccessDecision wilderness() {
        return WILDERNESS_ALLOW;
    }

    public static AccessDecision inClaim(Claim claim, Audience audience, boolean allowed, String reason) {
        return new AccessDecision(Objects.requireNonNull(claim, "claim"),
                Objects.requireNonNull(audience, "audience"), allowed, reason);
    }

    public Optional<Claim> claim() { return Optional.ofNullable(claim); }
    public Audience audience() { return audience; }
    public boolean allowed() { return allowed; }
    public boolean denied() { return !allowed; }
    public String reason() { return reason; }

    @Override
    public String toString() {
        return "AccessDecision{" + (allowed ? "ALLOW" : "DENY") + ", audience=" + audience
                + ", reason=" + reason + '}';
    }
}
