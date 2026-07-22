package dev.ankiesmp.dominium.paper.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory confirm-flow: één pending confirmation per (actor, kingdom, actie).
 * Verlopen na configureerbare TTL. Wist bij quit. Voorkomt dat een ouder confirm
 * per ongeluk een nieuwer command bevestigt.
 */
public final class ConfirmationStore {

    public enum Action { DISBAND, TRANSFER }

    private final long ttlMillis;
    private final LongSupplier clockMillis;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ConfirmationStore(long ttlSeconds) {
        this(ttlSeconds, System::currentTimeMillis);
    }

    public ConfirmationStore(long ttlSeconds, LongSupplier clockMillis) {
        if (ttlSeconds < 1) throw new IllegalArgumentException("ttlSeconds < 1");
        this.ttlMillis = ttlSeconds * 1000L;
        this.clockMillis = Objects.requireNonNull(clockMillis);
    }

    public void arm(UUID actor, Action action, UUID kingdomId, UUID targetIfAny) {
        pending.put(actor, new Pending(action, kingdomId, targetIfAny, clockMillis.getAsLong()));
    }

    public Optional<Pending> consume(UUID actor, Action expected, UUID expectedKingdom) {
        Pending p = pending.get(actor);
        if (p == null) return Optional.empty();
        if ((clockMillis.getAsLong() - p.armedAtMillis) > ttlMillis) {
            pending.remove(actor);
            return Optional.empty();
        }
        if (p.action != expected || !Objects.equals(p.kingdomId, expectedKingdom)) {
            return Optional.empty();
        }
        pending.remove(actor);
        return Optional.of(p);
    }

    public void clear(UUID actor) { pending.remove(actor); }

    public long ttlMillis() { return ttlMillis; }

    public record Pending(Action action, UUID kingdomId, UUID target, long armedAtMillis) {}
}
