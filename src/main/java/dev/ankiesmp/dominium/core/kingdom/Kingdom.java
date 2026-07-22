package dev.ankiesmp.dominium.core.kingdom;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Kingdom(UUID id, String displayName, String normalizedName,
                     Instant createdAt, Instant updatedAt) {
    public Kingdom {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(normalizedName);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
