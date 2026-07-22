package dev.ankiesmp.dominium.paper.command;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory confirm-flow voor {@code /dominium claims delete}. Actor-naam
 * plus claim-id moet exact matchen binnen de TTL — voorkomt dat een
 * verstrooide confirm per ongeluk de verkeerde claim wist.
 */
public final class ClaimDeleteConfirmations {

    private final long ttlMillis;
    private final LongSupplier clockMillis;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public ClaimDeleteConfirmations(long ttlSeconds) {
        this(ttlSeconds, System::currentTimeMillis);
    }

    public ClaimDeleteConfirmations(long ttlSeconds, LongSupplier clockMillis) {
        if (ttlSeconds < 1) throw new IllegalArgumentException("ttlSeconds < 1");
        this.ttlMillis = ttlSeconds * 1000L;
        this.clockMillis = Objects.requireNonNull(clockMillis);
    }

    public void arm(String actor, UUID claimId) {
        pending.put(actor, new Pending(claimId, clockMillis.getAsLong()));
    }

    public boolean consume(String actor, UUID claimId) {
        Pending p = pending.get(actor);
        if (p == null) return false;
        if ((clockMillis.getAsLong() - p.armedAtMillis) > ttlMillis) {
            pending.remove(actor);
            return false;
        }
        if (!p.claimId.equals(claimId)) return false;
        pending.remove(actor);
        return true;
    }

    public long ttlSeconds() { return ttlMillis / 1000L; }

    public void clear(String actor) { pending.remove(actor); }

    private record Pending(UUID claimId, long armedAtMillis) {}
}
