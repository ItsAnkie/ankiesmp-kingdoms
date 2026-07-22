package dev.ankiesmp.dominium.core.common;

import dev.ankiesmp.dominium.api.ClaimBlockHolderType;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable identifier voor een holder van claim blocks: ofwel een speler
 * (UUID) ofwel een kingdom (UUID). Wordt bewust als één value object
 * doorgegeven zodat een ledger-methode nooit een verkeerd type/id-paar kan
 * ontvangen.
 */
public final class HolderKey {

    private final ClaimBlockHolderType type;
    private final UUID id;

    private HolderKey(ClaimBlockHolderType type, UUID id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
    }

    public static HolderKey player(UUID playerId) {
        return new HolderKey(ClaimBlockHolderType.PLAYER, playerId);
    }

    public static HolderKey kingdom(UUID kingdomId) {
        return new HolderKey(ClaimBlockHolderType.KINGDOM, kingdomId);
    }

    public static HolderKey of(ClaimBlockHolderType type, UUID id) {
        return new HolderKey(type, id);
    }

    public ClaimBlockHolderType type() {
        return type;
    }

    public UUID id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HolderKey other)) return false;
        return type == other.type && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public String toString() {
        return type + ":" + id;
    }
}
