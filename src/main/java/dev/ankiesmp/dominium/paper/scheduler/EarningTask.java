package dev.ankiesmp.dominium.paper.scheduler;

import dev.ankiesmp.dominium.core.activity.ActivityTracker;
import dev.ankiesmp.dominium.core.earning.ActivePlayEarner;
import org.bukkit.Server;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Runs periodically; awards one interval-worth of blocks to each currently
 * online player die de laatste periode actief was. Idempotency-key wordt
 * per (player, day, interval-slot) berekend zodat crashes/retries niet
 * dubbel belonen.
 */
public final class EarningTask implements Runnable {

    private final ActivePlayEarner earner;
    private final ActivityTracker tracker;
    private final Executor dbExecutor;
    private final Logger log;
    private final Server server;

    public EarningTask(ActivePlayEarner earner, ActivityTracker tracker,
                       Executor dbExecutor, Logger log, Server server) {
        this.earner = Objects.requireNonNull(earner);
        this.tracker = Objects.requireNonNull(tracker);
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
        this.log = Objects.requireNonNull(log);
        this.server = Objects.requireNonNull(server);
    }

    @Override
    public void run() {
        if (!earner.enabled()) return;
        Instant now = Instant.now();
        long intervalSeconds = earner.config().intervalSeconds();
        long slot = now.getEpochSecond() / intervalSeconds;

        for (var player : server.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!tracker.isTracked(id)) continue;
            dbExecutor.execute(() -> {
                try {
                    earner.award(id, now, slot);
                } catch (RuntimeException ex) {
                    log.warn("Earning failed for {}: {}", id, ex.toString());
                }
            });
        }
    }
}
