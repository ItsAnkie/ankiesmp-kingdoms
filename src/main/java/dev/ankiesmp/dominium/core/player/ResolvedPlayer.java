package dev.ankiesmp.dominium.core.player;

import java.util.Objects;
import java.util.UUID;

/**
 * Speler die een {@link PlayerLookup} met zekerheid heeft geverifieerd:
 * ofwel {@code isOnline()} ofwel {@code hasPlayedBefore()} was waar op
 * het moment van resolven.
 */
public record ResolvedPlayer(UUID uuid, String name) {
    public ResolvedPlayer {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
    }
}
