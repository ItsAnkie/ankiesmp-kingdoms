package dev.ankiesmp.dominium.core.access;

import java.util.Objects;
import java.util.UUID;

public record PersonalClaimSettings(UUID claimId, boolean noAccess) {
    public PersonalClaimSettings {
        Objects.requireNonNull(claimId);
    }
    public static PersonalClaimSettings defaults(UUID claimId) {
        return new PersonalClaimSettings(claimId, false);
    }
}
