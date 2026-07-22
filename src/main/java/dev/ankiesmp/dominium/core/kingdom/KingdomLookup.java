package dev.ankiesmp.dominium.core.kingdom;

import java.util.Optional;
import java.util.UUID;

/** Hot-path lookup: membership + visitor voor kingdomterritory. Nooit blocking DB in listeners. */
public interface KingdomLookup {
    Optional<KingdomMember> membershipFor(UUID playerUuid);
    boolean isVisitor(UUID kingdomId, UUID playerUuid);
}
