package dev.ankiesmp.dominium.core.territory;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.AccessLookup;
import dev.ankiesmp.dominium.core.access.ClaimSettingsLookup;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.index.ClaimIndex;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.core.protection.Audience;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TerritoryContextCacheTest {

    private final WorldRef world = new WorldRef(UUID.randomUUID());

    private Claim addPersonalClaim(ClaimIndex index, UUID owner) {
        Claim c = new Claim(UUID.randomUUID(), world,
                ClaimRectangle.ofCorners(0, 0, 9, 9),
                ClaimOwner.personal(owner), Instant.now());
        index.add(c);
        return c;
    }

    // TC-001 — cache hit/miss counters kloppen.
    @Test
    void hitAndMissCountersReflectAccessLookup() {
        ClaimIndex idx = new ClaimIndex();
        AtomicInteger calls = new AtomicInteger();
        AccessLookup lookup = (c, p) -> { calls.incrementAndGet(); return Optional.empty(); };
        TerritoryContextCache cache = new TerritoryContextCache(idx, lookup, ClaimSettingsLookup.NEVER);

        UUID claim = UUID.randomUUID(), player = UUID.randomUUID();
        cache.levelFor(claim, player); // miss
        cache.levelFor(claim, player); // hit
        cache.levelFor(claim, player); // hit
        assertEquals(1, calls.get(), "upstream slechts één keer geraadpleegd");
        assertEquals(1, cache.missCount());
        assertEquals(2, cache.hitCount());
    }

    // TC-002 — invalidate per (claim, player) verwijdert exact die entry.
    @Test
    void invalidateAccessPerPlayer() {
        ClaimIndex idx = new ClaimIndex();
        AtomicInteger calls = new AtomicInteger();
        AccessLookup lookup = (c, p) -> {
            calls.incrementAndGet();
            return Optional.of(AccessLevel.TRUSTED);
        };
        TerritoryContextCache cache = new TerritoryContextCache(idx, lookup, ClaimSettingsLookup.NEVER);
        UUID c = UUID.randomUUID(), p = UUID.randomUUID(), other = UUID.randomUUID();

        cache.levelFor(c, p);
        cache.levelFor(c, other);
        assertEquals(2, calls.get());

        cache.invalidateAccess(c, p);
        cache.levelFor(c, p);
        cache.levelFor(c, other); // should still hit
        assertEquals(3, calls.get());
    }

    // TC-003 — invalidateClaim veegt alle audience-entries én settings weg.
    @Test
    void invalidateClaimSweepsAllRelated() {
        ClaimIndex idx = new ClaimIndex();
        AtomicInteger calls = new AtomicInteger();
        AccessLookup lookup = (c, p) -> {
            calls.incrementAndGet();
            return Optional.empty();
        };
        TerritoryContextCache cache = new TerritoryContextCache(idx, lookup, ClaimSettingsLookup.NEVER);
        UUID c = UUID.randomUUID();
        cache.levelFor(c, UUID.randomUUID());
        cache.levelFor(c, UUID.randomUUID());
        cache.levelFor(c, UUID.randomUUID());
        assertEquals(3, calls.get());
        cache.invalidateClaim(c);
        cache.levelFor(c, UUID.randomUUID());
        // Elk van de eerdere entries is weg; nieuwe player levert nieuwe miss.
        assertEquals(4, calls.get());
    }

    // TC-004 — settings-cache invalidation.
    @Test
    void noAccessInvalidation() {
        ClaimIndex idx = new ClaimIndex();
        AtomicInteger calls = new AtomicInteger();
        UUID claim = UUID.randomUUID();
        boolean[] backing = {false};
        ClaimSettingsLookup lookup = id -> { calls.incrementAndGet(); return backing[0]; };
        TerritoryContextCache cache = new TerritoryContextCache(idx, (c, p) -> Optional.empty(), lookup);
        assertFalse(cache.noAccess(claim));
        assertFalse(cache.noAccess(claim)); // hit
        backing[0] = true;
        assertFalse(cache.noAccess(claim), "stale hit tot invalidation");
        cache.invalidateSettings(claim);
        assertTrue(cache.noAccess(claim));
    }

    // TC-005 — TTL laat entries verlopen.
    @Test
    void ttlExpiryRefetches() {
        ClaimIndex idx = new ClaimIndex();
        AtomicInteger calls = new AtomicInteger();
        AccessLookup lookup = (c, p) -> { calls.incrementAndGet(); return Optional.empty(); };
        long[] now = {1_000_000L};
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now[0]), ZoneOffset.UTC);
        TerritoryContextCache cache = new TerritoryContextCache(idx, lookup, ClaimSettingsLookup.NEVER,
                50L, new Clock() {
                    @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                    @Override public Clock withZone(java.time.ZoneId z) { return this; }
                    @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
                    @Override public long millis() { return now[0]; }
                });
        UUID c = UUID.randomUUID(), p = UUID.randomUUID();
        cache.levelFor(c, p);
        cache.levelFor(c, p);
        assertEquals(1, calls.get());
        now[0] += 100; // > ttl
        cache.levelFor(c, p);
        assertEquals(2, calls.get(), "TTL-expiry moet refetchen");
    }

    // TC-006 — clear() zet de cache leeg.
    @Test
    void clearRemovesEverything() {
        ClaimIndex idx = new ClaimIndex();
        TerritoryContextCache cache = new TerritoryContextCache(
                idx, (c, p) -> Optional.empty(), ClaimSettingsLookup.NEVER);
        cache.levelFor(UUID.randomUUID(), UUID.randomUUID());
        cache.noAccess(UUID.randomUUID());
        assertTrue(cache.approximateSize() > 0);
        cache.clear();
        assertEquals(0, cache.approximateSize());
    }

    // TC-007 — contextAt vertaalt bucket-hit + audience + no-access naar TerritoryContext.
    @Test
    void contextAtProducesCorrectAudienceAndNoAccessFlag() {
        UUID owner = UUID.randomUUID();
        ClaimIndex idx = new ClaimIndex();
        Claim c = addPersonalClaim(idx, owner);
        boolean[] na = {false};
        AccessLookup lookup = (cid, pid) -> pid.equals(owner) ? Optional.of(AccessLevel.TRUSTED) : Optional.empty();
        // Owner check gebeurt via cache voordat lookup wordt aangeroepen — voor deze test
        // hebben we een aparte trusted UUID nodig.
        UUID trusted = UUID.randomUUID();
        AccessLookup lookup2 = (cid, pid) -> pid.equals(trusted) ? Optional.of(AccessLevel.TRUSTED) : Optional.empty();
        TerritoryContextCache cache = new TerritoryContextCache(idx, lookup2, id -> na[0]);

        var ownerCtx = cache.contextAt(world, 3, 3, owner);
        assertEquals(Audience.PERSONAL_OWNER, ownerCtx.audience());

        var trustedCtx = cache.contextAt(world, 3, 3, trusted);
        assertEquals(Audience.TRUSTED_PLAYER, trustedCtx.audience());

        var publicCtx = cache.contextAt(world, 3, 3, UUID.randomUUID());
        assertEquals(Audience.PUBLIC, publicCtx.audience());
        assertFalse(publicCtx.noAccess());

        na[0] = true;
        cache.invalidateSettings(c.id());
        var noAccessCtx = cache.contextAt(world, 3, 3, UUID.randomUUID());
        assertTrue(noAccessCtx.noAccess());

        var wilderness = cache.contextAt(world, 999, 999, UUID.randomUUID());
        assertFalse(wilderness.inClaim());
    }
}
