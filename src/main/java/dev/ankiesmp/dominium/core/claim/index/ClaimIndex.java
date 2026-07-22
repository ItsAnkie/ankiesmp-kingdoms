package dev.ankiesmp.dominium.core.claim.index;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.common.WorldRef;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-wereld chunk-bucket-index over claims. Één bucket bevat alleen de
 * candidate-set claim-ID's die de betreffende chunk raken; ownership
 * wordt daarna exact bepaald door {@link ClaimRectangle#contains}.
 *
 * <p>Deze klasse is <b>niet</b> thread-safe: mutaties zijn bedoeld voor
 * de serverthread. Reads mogen ook alleen daar plaatsvinden zodat we
 * geen extra synchronisatiekosten introduceren in hot paths.
 */
public final class ClaimIndex {

    private final Map<UUID, WorldClaimIndex> worlds = new HashMap<>();
    private final Map<UUID, Claim> byId = new LinkedHashMap<>();

    public Optional<Claim> get(UUID claimId) {
        return Optional.ofNullable(byId.get(claimId));
    }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /** Vindt de (unieke) claim van een gegeven owner-type + owner-id, indien aanwezig. */
    public Optional<Claim> findByOwner(dev.ankiesmp.dominium.core.claim.ClaimType type, UUID ownerId) {
        for (Claim c : byId.values()) {
            if (c.owner().type() == type && c.owner().id().equals(ownerId)) return Optional.of(c);
        }
        return Optional.empty();
    }

    /** Alle claims in de gegeven wereld (unordered). */
    public Collection<Claim> inWorld(WorldRef world) {
        WorldClaimIndex w = worlds.get(world.id());
        if (w == null) return List.of();
        Set<UUID> seen = new HashSet<>();
        List<Claim> out = new java.util.ArrayList<>();
        for (var idSet : w.buckets.values()) {
            for (UUID id : idSet) {
                if (seen.add(id)) out.add(byId.get(id));
            }
        }
        return out;
    }

    /** Alle claims die met de gegeven rectangle overlappen, exact via geometry. */
    public Set<Claim> overlapping(WorldRef world, ClaimRectangle rect) {
        WorldClaimIndex w = worlds.get(world.id());
        if (w == null) return Set.of();
        Set<Claim> out = new HashSet<>();
        for (long chunkKey : coveredChunks(rect)) {
            Set<UUID> bucket = w.buckets.get(chunkKey);
            if (bucket == null) continue;
            for (UUID id : bucket) {
                Claim c = byId.get(id);
                // Exact multi-region check: overlap tegen elke regio.
                if (c != null && c.geometry().intersects(rect)) out.add(c);
            }
        }
        return out;
    }

    /** Bevattend claim (indien uniek) op een X/Z-punt — via authoritative geometry. */
    public Optional<Claim> containing(WorldRef world, int x, int z) {
        WorldClaimIndex w = worlds.get(world.id());
        if (w == null) return Optional.empty();
        Set<UUID> bucket = w.buckets.get(ChunkKey.pack(ChunkKey.toChunk(x), ChunkKey.toChunk(z)));
        if (bucket == null) return Optional.empty();
        for (UUID id : bucket) {
            Claim c = byId.get(id);
            // Bucket-hit is candidate via chunk; exact contains via geometry
            // zodat gaten binnen bounds wilderness blijven.
            if (c != null && c.geometry().contains(x, z)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public void add(Claim claim) {
        if (byId.containsKey(claim.id())) {
            throw new IllegalStateException("claim already indexed: " + claim.id());
        }
        byId.put(claim.id(), claim);
        WorldClaimIndex w = worlds.computeIfAbsent(claim.world().id(), k -> new WorldClaimIndex());
        // Iedere regio krijgt zijn eigen bucket-set zodat claims met een gat
        // niet worden geraakt door bucket-hits binnen dat gat.
        for (dev.ankiesmp.dominium.core.claim.ClaimRectangle r : claim.geometry().regions()) {
            for (long chunk : coveredChunks(r)) {
                w.buckets.computeIfAbsent(chunk, k -> new HashSet<>()).add(claim.id());
            }
        }
    }

    /** Vervangt de geometrie van een bestaande claim (single-rect) en re-indexeert. */
    public Claim replace(UUID id, ClaimRectangle newRect) {
        return replaceGeometry(id, dev.ankiesmp.dominium.core.claim.ClaimGeometry.ofRectangle(newRect));
    }

    /** Vervangt de volledige (multi-region) geometry en re-indexeert. */
    public Claim replaceGeometry(UUID id, dev.ankiesmp.dominium.core.claim.ClaimGeometry newGeometry) {
        Claim existing = byId.get(id);
        if (existing == null) throw new IllegalStateException("unknown claim " + id);
        remove(id);
        Claim updated = existing.withGeometry(newGeometry);
        add(updated);
        return updated;
    }

    public void remove(UUID id) {
        Claim removed = byId.remove(id);
        if (removed == null) return;
        WorldClaimIndex w = worlds.get(removed.world().id());
        if (w == null) return;
        for (dev.ankiesmp.dominium.core.claim.ClaimRectangle r : removed.geometry().regions()) {
            for (long chunk : coveredChunks(r)) {
                Set<UUID> bucket = w.buckets.get(chunk);
                if (bucket == null) continue;
                bucket.remove(id);
                if (bucket.isEmpty()) w.buckets.remove(chunk);
            }
        }
    }

    private static long[] coveredChunks(ClaimRectangle rect) {
        int minCx = ChunkKey.toChunk(rect.minX());
        int minCz = ChunkKey.toChunk(rect.minZ());
        int maxCx = ChunkKey.toChunk(rect.maxX());
        int maxCz = ChunkKey.toChunk(rect.maxZ());
        int w = maxCx - minCx + 1;
        int d = maxCz - minCz + 1;
        long[] out = new long[Math.multiplyExact(w, d)];
        int idx = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                out[idx++] = ChunkKey.pack(cx, cz);
            }
        }
        return out;
    }

    private static final class WorldClaimIndex {
        final Map<Long, Set<UUID>> buckets = new HashMap<>();
    }
}
