package dev.ankiesmp.dominium.core.kingdom;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record KingdomMember(UUID kingdomId, UUID playerUuid, KingdomRole role,
                            Instant joinedAt, Optional<Instant> promotedAt) {
    public KingdomMember {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(playerUuid);
        Objects.requireNonNull(role);
        Objects.requireNonNull(joinedAt);
        Objects.requireNonNull(promotedAt);
    }
}
