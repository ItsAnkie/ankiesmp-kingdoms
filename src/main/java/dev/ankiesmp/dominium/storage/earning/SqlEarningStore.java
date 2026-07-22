package dev.ankiesmp.dominium.storage.earning;

import dev.ankiesmp.dominium.core.earning.EarningStore;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.UUID;

public final class SqlEarningStore implements EarningStore {

    private final Database db;

    public SqlEarningStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    @Override
    public long reserveDailyEarning(UUID playerUuid, long activeDay,
                                    long requested, long dailyCap, long updatedAtEpochMillis) {
        if (requested <= 0 || dailyCap <= 0) return 0;
        return db.withTransaction(conn -> {
            long already = readEarned(conn, playerUuid, activeDay);
            long remaining = Math.max(0, dailyCap - already);
            long grant = Math.min(requested, remaining);
            if (grant <= 0) return 0L;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO player_earning_state" +
                            "(player_uuid, active_day, blocks_earned, updated_at) " +
                            "VALUES (?, ?, ?, ?) " +
                            "ON CONFLICT(player_uuid, active_day) DO UPDATE SET " +
                            "  blocks_earned = player_earning_state.blocks_earned + ?, " +
                            "  updated_at = excluded.updated_at")) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, activeDay);
                ps.setLong(3, grant);
                ps.setLong(4, updatedAtEpochMillis);
                ps.setLong(5, grant);
                ps.executeUpdate();
            }
            return grant;
        });
    }

    @Override
    public long earnedToday(UUID playerUuid, long activeDay) {
        return db.withConnection(conn -> readEarned(conn, playerUuid, activeDay));
    }

    private static long readEarned(java.sql.Connection conn, UUID playerUuid, long activeDay)
            throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT blocks_earned FROM player_earning_state " +
                        "WHERE player_uuid = ? AND active_day = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, activeDay);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0L;
                return rs.getLong(1);
            }
        }
    }
}
