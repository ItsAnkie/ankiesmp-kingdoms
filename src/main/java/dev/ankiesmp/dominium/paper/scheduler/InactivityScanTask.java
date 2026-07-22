package dev.ankiesmp.dominium.paper.scheduler;

import dev.ankiesmp.dominium.core.expiry.InactivityExpiryService;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Roept de {@link InactivityExpiryService#scanAndExpire} op de db-executor.
 * De service beslist zelf op basis van de {@link Predicate} of een owner
 * momenteel online is; we geven daarom een Bukkit-backed predicate mee.
 *
 * <p>De predicate wordt vanuit de asynchrone task aangeroepen — voor
 * {@code Server#getPlayer(UUID)} is dat volgens Paper's contract veilig
 * (geeft snapshot van online-lijst terug).
 */
public final class InactivityScanTask implements Runnable {

    private final InactivityExpiryService expiry;
    private final Executor dbExecutor;
    private final Logger log;
    private final Predicate<UUID> onlinePredicate;

    public InactivityScanTask(InactivityExpiryService expiry, Executor dbExecutor,
                              Logger log, Predicate<UUID> onlinePredicate) {
        this.expiry = Objects.requireNonNull(expiry);
        this.dbExecutor = Objects.requireNonNull(dbExecutor);
        this.log = Objects.requireNonNull(log);
        this.onlinePredicate = Objects.requireNonNull(onlinePredicate);
    }

    @Override
    public void run() {
        dbExecutor.execute(() -> {
            try {
                var report = expiry.scanAndExpire();
                if (report.expiredCount() > 0) {
                    log.info("Inactivity scan: expired {} claim(s), skipped {}, owners checked {}.",
                            report.expiredCount(), report.skipped(), report.ownersChecked());
                }
            } catch (RuntimeException ex) {
                log.warn("Inactivity scan failed: {}", ex.toString());
            }
        });
    }

    public Predicate<UUID> onlinePredicate() { return onlinePredicate; }
}
