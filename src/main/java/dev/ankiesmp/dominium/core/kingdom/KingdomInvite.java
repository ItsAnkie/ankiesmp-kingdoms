package dev.ankiesmp.dominium.core.kingdom;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KingdomInvite(long id, UUID kingdomId, UUID targetUuid, UUID inviterUuid,
                            Instant createdAt, Instant expiresAt) {
    public KingdomInvite {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(targetUuid);
        Objects.requireNonNull(inviterUuid);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expiresAt);
    }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
