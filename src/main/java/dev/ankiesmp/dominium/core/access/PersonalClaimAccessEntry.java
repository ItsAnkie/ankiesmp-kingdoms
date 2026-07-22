package dev.ankiesmp.dominium.core.access;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PersonalClaimAccessEntry(
        UUID claimId, UUID playerUuid, AccessLevel level, Instant addedAt, String addedBy) {
    public PersonalClaimAccessEntry {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(playerUuid);
        Objects.requireNonNull(level);
        Objects.requireNonNull(addedAt);
        Objects.requireNonNull(addedBy);
    }
}
