package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.AccessLookup;
import dev.ankiesmp.dominium.core.access.ClaimSettingsLookup;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.protection.Audience;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gedeelde cache voor protection hot path, No Access movement checks,
 * territory HUD en audience resolution.
 *
 * <p>Twee lagen:
 * <ul>
 *   <li><b>Per-claim audience</b> {@code (claimId, playerId) → Audience}:
 *       cache van {@link AccessLookup} resultaten met per-eintry-invalidation
 *       bij trust/untrust/visitor mutaties.</li>
 *   <li><b>Per-claim settings</b> {@code claimId → noAccess}: cache van
 *       {@link ClaimSettingsLookup} results, invalidated bij no-access toggle.</li>
 * </ul>
 *
 * <p>Spatial lookups worden bewust <b>niet</b> gecached — de bestaande
 * {@link ClaimIndex} is al de spatial-index, en dubbele cache introduceert
 * consistentierisico. {@link #contextAt} doet een index-lookup en wraps
 * met (gecachede) audience + settings.
 *
 * <p><b>Threading:</b> methods gebruiken {@link ConcurrentHashMap} en zijn
 * safe voor concurrent access. Bukkit API wordt hier nooit aangeroepen.
 *
 * <p><b>Invalidation:</b> alle mutaties (create/resize/delete claim,
 * trust/untrust, visitor add/remove, no-access toggle, player quit, world
 * change, plugin disable/reload) moeten via de {@code invalidate*}-methods
 * of {@link #clear()} lopen. Cache-writes gebeuren pas nadat de DB-transactie
 * committed is (de PersonalClaimAccessService roept invalidator ná store-call).
 *
 * <p>TTL is een safety-net (60s default) tegen missende invalidations.
 */
public final class TerritoryContextCache implements AccessLookup, ClaimSettingsLookup {

    private static final long DEFAULT_TTL_MILLIS = 60_000L;

    private final ClaimIndex index;
    private final AccessLookup upstreamAccess;
    private final ClaimSettingsLookup upstreamSettings;
    private final long ttlMillis;
    private final java.time.Clock clock;

    private final Map<AudienceKey, TimedValue<Optional<AccessLevel>>> accessCache = new ConcurrentHashMap<>();
    private final Map<UUID, TimedValue<Boolean>> settingsCache = new ConcurrentHashMap<>();

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong invalidations = new AtomicLong();

    public TerritoryContextCache(ClaimIndex index,
                                 AccessLookup upstreamAccess,
                                 ClaimSettingsLookup upstreamSettings) {
        this(index, upstreamAccess, upstreamSettings, DEFAULT_TTL_MILLIS, java.time.Clock.systemUTC());
    }

    public TerritoryContextCache(ClaimIndex index,
                                 AccessLookup upstreamAccess,
                                 ClaimSettingsLookup upstreamSettings,
                                 long ttlMillis,
                                 java.time.Clock clock) {
        this.index = Objects.requireNonNull(index);
        this.upstreamAccess = Objects.requireNonNull(upstreamAccess);
        this.upstreamSettings = Objects.requireNonNull(upstreamSettings);
        this.ttlMillis = ttlMillis;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Optional<AccessLevel> levelFor(UUID claimId, UUID playerId) {
        AudienceKey key = new AudienceKey(claimId, playerId);
        long now = clock.millis();
        TimedValue<Optional<AccessLevel>> v = accessCache.get(key);
        if (v != null && !v.expired(now, ttlMillis)) {
            hits.incrementAndGet();
            return v.value();
        }
        misses.incrementAndGet();
        Optional<AccessLevel> fresh = upstreamAccess.levelFor(claimId, playerId);
        accessCache.put(key, new TimedValue<>(fresh, now));
        return fresh;
    }

    @Override
    public boolean noAccess(UUID claimId) {
        long now = clock.millis();
        TimedValue<Boolean> v = settingsCache.get(claimId);
        if (v != null && !v.expired(now, ttlMillis)) {
            hits.incrementAndGet();
            return v.value();
        }
        misses.incrementAndGet();
        boolean fresh = upstreamSettings.noAccess(claimId);
        settingsCache.put(claimId, new TimedValue<>(fresh, now));
        return fresh;
    }

    public TerritoryContext contextAt(WorldRef world, int x, int z, UUID actorId) {
        Optional<Claim> claim = index.containing(world, x, z);
        if (claim.isEmpty()) return TerritoryContext.wilderness();
        Claim c = claim.get();
        Audience audience = resolveAudience(c, actorId);
        boolean na = c.owner().type() == ClaimType.PERSONAL && noAccess(c.id());
        return new TerritoryContext(claim, audience, na);
    }

    private Audience resolveAudience(Claim claim, UUID actorId) {
        if (actorId == null) return Audience.PUBLIC;
        if (claim.owner().type() == ClaimType.PERSONAL && claim.owner().id().equals(actorId)) {
            return Audience.PERSONAL_OWNER;
        }
        if (claim.owner().type() != ClaimType.PERSONAL) return Audience.PUBLIC;
        Optional<AccessLevel> level = levelFor(claim.id(), actorId);
        if (level.isEmpty()) return Audience.PUBLIC;
        return switch (level.get()) {
            case TRUSTED -> Audience.TRUSTED_PLAYER;
            case VISITOR -> Audience.PERSONAL_VISITOR;
        };
    }

    // ---- invalidation hooks ----

    public void invalidateAccess(UUID claimId, UUID playerId) {
        accessCache.remove(new AudienceKey(claimId, playerId));
        invalidations.incrementAndGet();
    }

    public void invalidateClaim(UUID claimId) {
        accessCache.keySet().removeIf(k -> k.claimId.equals(claimId));
        settingsCache.remove(claimId);
        invalidations.incrementAndGet();
    }

    public void invalidatePlayer(UUID playerId) {
        accessCache.keySet().removeIf(k -> k.playerId.equals(playerId));
        invalidations.incrementAndGet();
    }

    public void invalidateSettings(UUID claimId) {
        settingsCache.remove(claimId);
        invalidations.incrementAndGet();
    }

    public void clear() {
        accessCache.clear();
        settingsCache.clear();
        invalidations.incrementAndGet();
    }

    public long hitCount()          { return hits.get(); }
    public long missCount()         { return misses.get(); }
    public long invalidationCount() { return invalidations.get(); }
    public int  approximateSize()   { return accessCache.size() + settingsCache.size(); }

    private record AudienceKey(UUID claimId, UUID playerId) {}
    private record TimedValue<T>(T value, long timestampMillis) {
        boolean expired(long now, long ttl) { return (now - timestampMillis) > ttl; }
    }
}
