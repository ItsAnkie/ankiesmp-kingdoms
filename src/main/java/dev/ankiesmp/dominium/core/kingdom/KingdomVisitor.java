package dev.ankiesmp.dominium.core.kingdom;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KingdomVisitor(UUID kingdomId, UUID playerUuid, UUID addedBy, Instant createdAt) {
    public KingdomVisitor {
        Objects.requireNonNull(kingdomId);
        Objects.requireNonNull(playerUuid);
        Objects.requireNonNull(addedBy);
        Objects.requireNonNull(createdAt);
    }
}
