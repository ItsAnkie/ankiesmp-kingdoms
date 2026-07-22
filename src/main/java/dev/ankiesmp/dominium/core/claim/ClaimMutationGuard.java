package dev.ankiesmp.dominium.core.claim;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Beperkt claimmutaties per owner. Wordt door {@link ClaimService} vóór
 * elke create/resize/delete geraadpleegd. In normale runs is de
 * {@link #ALLOW_ALL}-variant actief; tijdens CLAIM_REPAIR_MODE bevat de
 * guard exact de conflicterende owners uit
 * {@code SingleClaimIndexInstaller.findConflicts}.
 *
 * <p>Deze check werkt ook <b>zonder</b> DB-index — dat is bewust, zodat
 * we een tweede claim voor dezelfde owner ook op de application-service
 * kunnen weigeren wanneer de unique index nog niet is geïnstalleerd.
 */
public interface ClaimMutationGuard {

    boolean isBlocked(ClaimType type, UUID ownerId);

    /** Menselijke reden die aan de speler getoond wordt bij een blok. */
    String reason(ClaimType type, UUID ownerId);

    ClaimMutationGuard ALLOW_ALL = new ClaimMutationGuard() {
        @Override public boolean isBlocked(ClaimType t, UUID o) { return false; }
        @Override public String reason(ClaimType t, UUID o) { return ""; }
    };

    /** Bouwer voor de repair-mode guard. */
    final class Builder {
        private final Set<Key> blocked = new HashSet<>();
        public Builder block(ClaimType type, UUID ownerId) {
            blocked.add(new Key(type, ownerId));
            return this;
        }
        public ClaimMutationGuard build() {
            Set<Key> snapshot = Set.copyOf(blocked);
            return new ClaimMutationGuard() {
                @Override public boolean isBlocked(ClaimType t, UUID o) {
                    return snapshot.contains(new Key(t, o));
                }
                @Override public String reason(ClaimType t, UUID o) {
                    return "Claim mutations for this owner are blocked while duplicate "
                            + "claims are being repaired. Use /dominium claims inspect and "
                            + "/dominium claims delete to resolve.";
                }
            };
        }
    }

    record Key(ClaimType type, UUID ownerId) {
        public Key { Objects.requireNonNull(type); Objects.requireNonNull(ownerId); }
    }
}
