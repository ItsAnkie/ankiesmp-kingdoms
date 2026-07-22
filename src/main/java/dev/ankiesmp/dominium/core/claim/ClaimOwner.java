package dev.ankiesmp.dominium.core.claim;

import dev.ankiesmp.dominium.api.ClaimBlockHolderType;
import dev.ankiesmp.dominium.core.common.HolderKey;

import java.util.Objects;
import java.util.UUID;

/**
 * Eigenaar van een claim. Voor {@link ClaimType#PERSONAL} verwijst het
 * naar een speler-UUID; voor {@link ClaimType#KINGDOM} naar een
 * kingdom-UUID; voor {@link ClaimType#ADMIN} kan het admin-scoped zijn
 * (fase &gt; 1).
 */
public final class ClaimOwner {

    private final ClaimType type;
    private final UUID id;

    private ClaimOwner(ClaimType type, UUID id) {
        this.type = Objects.requireNonNull(type, "type");
        this.id = Objects.requireNonNull(id, "id");
    }

    public static ClaimOwner personal(UUID playerId) { return new ClaimOwner(ClaimType.PERSONAL, playerId); }
    public static ClaimOwner kingdom(UUID kingdomId) { return new ClaimOwner(ClaimType.KINGDOM, kingdomId); }
    public static ClaimOwner admin(UUID scopeId) { return new ClaimOwner(ClaimType.ADMIN, scopeId); }

    public ClaimType type() { return type; }
    public UUID id() { return id; }

    public HolderKey toLedgerHolder() {
        return switch (type) {
            case PERSONAL -> HolderKey.of(ClaimBlockHolderType.PLAYER, id);
            case KINGDOM  -> HolderKey.of(ClaimBlockHolderType.KINGDOM, id);
            case ADMIN    -> throw new IllegalStateException("admin claims do not draw from a ledger holder");
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaimOwner c)) return false;
        return type == c.type && id.equals(c.id);
    }

    @Override
    public int hashCode() { return Objects.hash(type, id); }

    @Override
    public String toString() { return type + ":" + id; }
}
