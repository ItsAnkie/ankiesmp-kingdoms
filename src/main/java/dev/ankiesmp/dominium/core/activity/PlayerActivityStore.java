package dev.ankiesmp.dominium.core.activity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistente activity state per speler. Batched: de {@link ActivityTracker}
 * verzamelt in-memory en schrijft via {@link #flushBatch(Map, long)}.
 */
public interface PlayerActivityStore {

    void flushBatch(Map<UUID, ActivityDelta> deltas, long updatedAtEpochMillis);

    Optional<ActivitySnapshot> load(UUID playerId);

    record ActivityDelta(long additionalActiveSeconds, long lastActiveAtEpochMillis) {}

    record ActivitySnapshot(UUID playerId, long lastActiveAtEpochMillis, long totalActiveSeconds) {}
}
