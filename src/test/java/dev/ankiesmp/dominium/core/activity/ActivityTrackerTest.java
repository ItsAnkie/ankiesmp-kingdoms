package dev.ankiesmp.dominium.core.activity;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActivityTrackerTest {

    private long nanos = 0L;
    private long millis = 0L;

    private ActivityTracker tracker(long windowSeconds) {
        return new ActivityTracker(windowSeconds, () -> nanos, () -> millis);
    }

    private void advanceSeconds(long s) {
        nanos += s * 1_000_000_000L;
        millis += s * 1_000L;
    }

    // AT-001 — actieve tijd tussen twee acties telt.
    @Test
    void continuousActivityAccrues() {
        var t = tracker(60);
        UUID p = UUID.randomUUID();
        t.onJoin(p);
        advanceSeconds(30);
        t.recordActivity(p);
        var drain = t.drainAll();
        assertEquals(30, drain.get(p).additionalActiveSeconds());
    }

    // AT-002 — AFK-tijd telt niet.
    @Test
    void afkGapDoesNotAccrue() {
        var t = tracker(60);
        UUID p = UUID.randomUUID();
        t.onJoin(p);
        advanceSeconds(5); t.recordActivity(p); // +5
        advanceSeconds(500); t.recordActivity(p); // AFK > window → reset, geen krediet
        advanceSeconds(10); t.recordActivity(p);  // +10
        var drain = t.drainAll();
        assertEquals(15, drain.get(p).additionalActiveSeconds(),
                "5 + 10 = 15, de AFK-periode telt niet");
    }

    // AT-003 — drainAll reset counters maar houdt sessie in leven.
    @Test
    void drainResetsAndKeepsSession() {
        var t = tracker(60);
        UUID p = UUID.randomUUID();
        t.onJoin(p);
        advanceSeconds(20); t.recordActivity(p);
        assertEquals(20, t.drainAll().get(p).additionalActiveSeconds());
        assertTrue(t.isTracked(p));
        var second = t.drainAll();
        assertNull(second.get(p), "geen nieuwe accrue → geen entry in de volgende drain");
    }

    // AT-004 — quit levert de resterende delta.
    @Test
    void quitReturnsAccrued() {
        var t = tracker(60);
        UUID p = UUID.randomUUID();
        t.onJoin(p);
        advanceSeconds(12); t.recordActivity(p);
        var delta = t.onQuit(p);
        assertNotNull(delta);
        assertEquals(12, delta.additionalActiveSeconds());
        assertFalse(t.isTracked(p));
    }

    // AT-005 — reconnect start bij 0.
    @Test
    void reconnectStartsFresh() {
        var t = tracker(60);
        UUID p = UUID.randomUUID();
        t.onJoin(p);
        advanceSeconds(50); t.recordActivity(p);
        t.onQuit(p);
        // Reconnect
        advanceSeconds(300);
        t.onJoin(p);
        advanceSeconds(10); t.recordActivity(p);
        var drain = t.drainAll();
        assertEquals(10, drain.get(p).additionalActiveSeconds());
    }

    // AT-006 — meerdere spelers werken onafhankelijk.
    @Test
    void multiplePlayers() {
        var t = tracker(60);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        t.onJoin(a); t.onJoin(b);
        advanceSeconds(15);
        t.recordActivity(a);
        advanceSeconds(20);
        t.recordActivity(b);
        Map<UUID, PlayerActivityStore.ActivityDelta> drain = t.drainAll();
        assertEquals(15, drain.get(a).additionalActiveSeconds());
        assertEquals(35, drain.get(b).additionalActiveSeconds());
    }
}
