package dev.ankiesmp.dominium.core.kingdom;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KingdomCacheTest {

    private final UUID kingdomId = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    private KingdomStore stubStore(AtomicInteger callCounter) {
        return new KingdomStore() {
            @Override public Optional<Kingdom> findKingdom(UUID id) { return Optional.empty(); }
            @Override public Optional<Kingdom> findKingdomByNormalizedName(String n) { return Optional.empty(); }
            @Override public List<Kingdom> listKingdoms() { return List.of(); }
            @Override public Kingdom createWithLeader(UUID k, String d, String n, UUID l, Instant a)
            { throw new UnsupportedOperationException(); }
            @Override public void disband(UUID k) {}
            @Override public void transferLeadership(UUID k, UUID a, UUID b, Instant t) {}

            @Override public Optional<KingdomMember> findMembership(UUID p) {
                callCounter.incrementAndGet();
                return Optional.of(new KingdomMember(kingdomId, p, KingdomRole.MEMBER,
                        Instant.EPOCH, Optional.empty()));
            }
            @Override public List<KingdomMember> listMembers(UUID k) { return List.of(); }
            @Override public void updateRole(UUID k, UUID p, KingdomRole r, Instant t) {}
            @Override public void removeMember(UUID k, UUID p) {}

            @Override public void deleteExpiredInvites(Instant t) {}
            @Override public Optional<KingdomInvite> findInvite(UUID k, UUID t) { return Optional.empty(); }
            @Override public List<KingdomInvite> invitesForTarget(UUID t) { return List.of(); }
            @Override public void insertInvite(UUID k, UUID t, UUID i, Instant a, Instant e) {}
            @Override public void deleteInvite(UUID k, UUID t) {}
            @Override public Optional<KingdomMember> acceptInvite(UUID k, UUID t, Instant n) { return Optional.empty(); }

            @Override public List<KingdomVisitor> listVisitors(UUID k) { return List.of(); }
            @Override public boolean isVisitor(UUID k, UUID p) {
                callCounter.incrementAndGet();
                return true;
            }
            @Override public void insertVisitor(UUID k, UUID p, UUID a, Instant t) {}
            @Override public void removeVisitor(UUID k, UUID p) {}
        };
    }

    @Test
    void hitAndMiss() {
        AtomicInteger calls = new AtomicInteger();
        var cache = new KingdomCache(stubStore(calls));
        cache.membershipFor(player);
        cache.membershipFor(player); // hit
        cache.isVisitor(kingdomId, player);
        cache.isVisitor(kingdomId, player); // hit
        assertEquals(2, calls.get());
        assertEquals(2, cache.hitCount());
        assertEquals(2, cache.missCount());
    }

    @Test
    void invalidatePlayerClearsBothMaps() {
        AtomicInteger calls = new AtomicInteger();
        var cache = new KingdomCache(stubStore(calls));
        cache.membershipFor(player);
        cache.isVisitor(kingdomId, player);
        cache.invalidatePlayer(player);
        cache.membershipFor(player);
        cache.isVisitor(kingdomId, player);
        assertEquals(4, calls.get());
    }

    @Test
    void invalidateKingdomSweepsRelated() {
        AtomicInteger calls = new AtomicInteger();
        var cache = new KingdomCache(stubStore(calls));
        cache.membershipFor(player);
        cache.isVisitor(kingdomId, player);
        cache.invalidateKingdom(kingdomId);
        // membership entry hoort bij dit kingdom (stub geeft altijd hetzelfde kingdom).
        cache.membershipFor(player);
        cache.isVisitor(kingdomId, player);
        assertEquals(4, calls.get());
    }
}
