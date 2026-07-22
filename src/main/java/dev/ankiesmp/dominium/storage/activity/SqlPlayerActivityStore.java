package dev.ankiesmp.dominium.storage.activity;

import dev.ankiesmp.dominium.core.activity.PlayerActivityStore;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqlPlayerActivityStore implements PlayerActivityStore {

    private final Database db;

    public SqlPlayerActivityStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    @Override
    public void flushBatch(Map<UUID, ActivityDelta> deltas, long updatedAtEpochMillis) {
        if (deltas.isEmpty()) return;
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_activity_state" +
                            "(player_uuid, last_active_at, total_active_seconds, updated_at) " +
                            "VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(player_uuid) DO UPDATE SET " +
                            "  last_active_at = excluded.last_active_at, " +
                            "  total_active_seconds = player_activity_state.total_active_seconds + ?, " +
                            "  updated_at = excluded.updated_at")) {
                for (var e : deltas.entrySet()) {
                    ActivityDelta d = e.getValue();
                    ps.setString(1, e.getKey().toString());
                    ps.setLong(2, d.lastActiveAtEpochMillis());
                    ps.setLong(3, d.additionalActiveSeconds());
                    ps.setLong(4, updatedAtEpochMillis);
                    ps.setLong(5, d.additionalActiveSeconds());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public Optional<ActivitySnapshot> load(UUID playerId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT last_active_at, total_active_seconds FROM player_activity_state " +
                            "WHERE player_uuid = ?")) {
                ps.setString(1, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.<ActivitySnapshot>empty();
                    return Optional.of(new ActivitySnapshot(
                            playerId, rs.getLong(1), rs.getLong(2)));
                }
            }
        });
    }
}
