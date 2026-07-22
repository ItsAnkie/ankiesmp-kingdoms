package dev.ankiesmp.dominium.core.claim;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable variant van {@link ClaimMutationGuard}. Gebruikt tijdens
 * CLAIM_REPAIR_MODE: bij succesvolle repair verwijdert het admin-command
 * de owner via {@link #unblock} zodat mutaties direct weer werken zonder
 * serverrestart.
 */
public final class MutableClaimMutationGuard implements ClaimMutationGuard {

    private final Set<ClaimMutationGuard.Key> blocked = ConcurrentHashMap.newKeySet();

    public MutableClaimMutationGuard() {}

    public void block(ClaimType type, UUID ownerId) {
        blocked.add(new ClaimMutationGuard.Key(type, ownerId));
    }

    public boolean unblock(ClaimType type, UUID ownerId) {
        return blocked.remove(new ClaimMutationGuard.Key(type, ownerId));
    }

    public boolean hasAny() { return !blocked.isEmpty(); }
    public int size() { return blocked.size(); }

    @Override
    public boolean isBlocked(ClaimType type, UUID ownerId) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(ownerId);
        return blocked.contains(new ClaimMutationGuard.Key(type, ownerId));
    }

    @Override
    public String reason(ClaimType type, UUID ownerId) {
        return "You currently have multiple legacy claims. "
                + "An administrator must repair them before you can modify your claim.";
    }
}
