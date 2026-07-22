package dev.ankiesmp.dominium.core.activity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory activity tracker. Sessies gebruiken monotonic nanoseconden;
 * flush-batches krijgen daarnaast een epoch-milli timestamp mee.
 *
 * <p>Regels:
 * <ul>
 *   <li>Iedere relevante actie ({@link #recordActivity}) verlengt de session
 *       met minimaal 1 seconde, gecapt op {@code activityWindowSeconds}
 *       zodat AFK-tijd na de laatste actie niet meetelt.</li>
 *   <li>Meerdere acties binnen 1 seconde tellen 1x — je kan geen
 *       actieve tijd verdienen door met een macro te klikken.</li>
 *   <li>{@link #onQuit} en {@link #drainAll} sluiten sessies netjes en
 *       leveren een batch aan de caller (die naar de {@link PlayerActivityStore}
 *       schrijft op de db-executor).</li>
 *   <li>De tracker houdt <b>geen</b> lifetime totals bij — dat doet de store.
 *       In-memory alleen delta sinds laatste flush.</li>
 * </ul>
 */
public final class ActivityTracker {

    private final long activityWindowNanos;
    private final LongSupplier nanoTime;
    private final LongSupplier epochMillis;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public ActivityTracker(long activityWindowSeconds,
                           LongSupplier nanoTime, LongSupplier epochMillis) {
        if (activityWindowSeconds <= 0) {
            throw new IllegalArgumentException("activityWindowSeconds must be > 0");
        }
        this.activityWindowNanos = activityWindowSeconds * 1_000_000_000L;
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.epochMillis = Objects.requireNonNull(epochMillis);
    }

    public void onJoin(UUID playerId) {
        Objects.requireNonNull(playerId);
        long now = nanoTime.getAsLong();
        sessions.computeIfAbsent(playerId, id -> new Session(now));
    }

    public void recordActivity(UUID playerId) {
        Objects.requireNonNull(playerId);
        long now = nanoTime.getAsLong();
        Session s = sessions.computeIfAbsent(playerId, id -> new Session(now));
        synchronized (s) {
            long sinceLast = now - s.lastActiveNanos;
            if (sinceLast <= 0) return;
            if (sinceLast > activityWindowNanos) {
                // Speler was langer dan het activity-window AFK — die tijd telt
                // niet mee. Reset de baseline zonder iets te crediten.
                s.lastActiveNanos = now;
                return;
            }
            s.accruedNanos += sinceLast;
            s.lastActiveNanos = now;
        }
    }

    /**
     * Snapshot voor batch flush; reset de accrued-tellers atomair.
     * Verwijdert de sessie <b>niet</b> — de speler kan nog online zijn.
     */
    public Map<UUID, PlayerActivityStore.ActivityDelta> drainAll() {
        long now = nanoTime.getAsLong();
        long epoch = epochMillis.getAsLong();
        Map<UUID, PlayerActivityStore.ActivityDelta> out = new HashMap<>();
        for (var e : sessions.entrySet()) {
            Session s = e.getValue();
            long delta;
            synchronized (s) {
                delta = s.accruedNanos;
                s.accruedNanos = 0;
                if (delta > 0) s.lastFlushedEpochMillis = epoch;
            }
            long seconds = delta / 1_000_000_000L;
            if (seconds <= 0) continue;
            out.put(e.getKey(), new PlayerActivityStore.ActivityDelta(seconds, epoch));
        }
        return out;
    }

    public PlayerActivityStore.ActivityDelta onQuit(UUID playerId) {
        Session s = sessions.remove(playerId);
        if (s == null) return null;
        long delta;
        synchronized (s) {
            delta = s.accruedNanos;
        }
        long seconds = delta / 1_000_000_000L;
        if (seconds <= 0) return null;
        return new PlayerActivityStore.ActivityDelta(seconds, epochMillis.getAsLong());
    }

    public boolean isTracked(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public int activeSessionCount() { return sessions.size(); }

    private static final class Session {
        long lastActiveNanos;
        long accruedNanos;
        long lastFlushedEpochMillis;
        Session(long startNanos) {
            this.lastActiveNanos = startNanos;
        }
    }
}
