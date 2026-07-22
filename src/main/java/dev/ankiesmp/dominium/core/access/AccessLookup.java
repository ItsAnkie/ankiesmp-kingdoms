package dev.ankiesmp.dominium.core.access;

import java.util.Optional;
import java.util.UUID;

/** Hot-path lookup: welk access-level heeft deze speler op deze claim? */
@FunctionalInterface
public interface AccessLookup {
    Optional<AccessLevel> levelFor(UUID claimId, UUID playerId);
}
