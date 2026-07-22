package dev.ankiesmp.dominium.paper.scheduler;

import dev.ankiesmp.dominium.core.activity.ActivityTracker;
import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Periodieke flush van accrued session-tijd naar de {@link PlayerActivityStore}.
 * Draait op async scheduler; het écht schrijven gebeurt op de db-executor.
 */
public final class ActivityFlushTask implements Runnable {

    private final ActivityTracker tracker;
    private final PlayerActivityStore store;
    private final Executor dbExecutor;
    private final Logger log;

    public ActivityFlushTask(ActivityTracker tracker, PlayerActivityStore store,
                             Executor dbExecutor, Logger log) {
        this.tracker = Objects.requireNonNull(tracker);
        this.store = Objects.requireNonNull(store);
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public void run() {
        Map<UUID, PlayerActivityStore.ActivityDelta> batch = tracker.drainAll();
        if (batch.isEmpty()) return;
        long now = System.currentTimeMillis();
        dbExecutor.execute(() -> {
            try {
                store.flushBatch(batch, now);
            } catch (RuntimeException ex) {
                log.warn("Activity flush failed for {} players: {}", batch.size(), ex.toString());
            }
        });
    }
}
