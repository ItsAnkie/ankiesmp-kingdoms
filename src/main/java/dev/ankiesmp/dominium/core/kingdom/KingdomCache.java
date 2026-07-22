package dev.ankiesmp.dominium.core.kingdom;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache voor kingdom-membership per player-UUID en visitor-membership per
 * (kingdom, player). Mutaties gaan altijd via {@link
 * KingdomService.Invalidator}: revoke geldt direct.
 *
 * <p>Geen TTL: mutaties invalideren expliciet, en de dataset is klein
 * genoeg om per-item te bewaren (typisch honderden spelers).
 */
public final class KingdomCache implements KingdomLookup {

    private final KingdomStore upstream;
    private final Map<UUID, Optional<KingdomMember>> memberships = new ConcurrentHashMap<>();
    private final Map<VisitorKey, Boolean> visitors = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public KingdomCache(KingdomStore upstream) {
        this.upstream = Objects.requireNonNull(upstream);
    }

    @Override
    public Optional<KingdomMember> membershipFor(UUID playerUuid) {
        var cached = memberships.get(playerUuid);
        if (cached != null) { hits.incrementAndGet(); return cached; }
        misses.incrementAndGet();
        var fresh = upstream.findMembership(playerUuid);
        memberships.put(playerUuid, fresh);
        return fresh;
    }

    @Override
    public boolean isVisitor(UUID kingdomId, UUID playerUuid) {
        VisitorKey key = new VisitorKey(kingdomId, playerUuid);
        Boolean cached = visitors.get(key);
        if (cached != null) { hits.incrementAndGet(); return cached; }
        misses.incrementAndGet();
        boolean fresh = upstream.isVisitor(kingdomId, playerUuid);
        visitors.put(key, fresh);
        return fresh;
    }

    public void invalidatePlayer(UUID playerUuid) {
        memberships.remove(playerUuid);
        visitors.keySet().removeIf(k -> k.playerUuid.equals(playerUuid));
        invalidations.incrementAndGet();
    }

    public void invalidateKingdom(UUID kingdomId) {
        // Wis alle memberships waarvan we weten dat ze bij dit kingdom hoorden,
        // plus alle visitor-entries voor dit kingdom.
        memberships.entrySet().removeIf(e ->
                e.getValue().isPresent() && e.getValue().get().kingdomId().equals(kingdomId));
        visitors.keySet().removeIf(k -> k.kingdomId.equals(kingdomId));
        invalidations.incrementAndGet();
    }

    public void clear() {
        memberships.clear();
        visitors.clear();
        invalidations.incrementAndGet();
    }

    public long hitCount()          { return hits.get(); }
    public long missCount()         { return misses.get(); }
    public long invalidationCount() { return invalidations.get(); }

    private record VisitorKey(UUID kingdomId, UUID playerUuid) {}
}
