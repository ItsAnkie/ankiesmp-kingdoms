package dev.ankiesmp.dominium.storage.access;

import dev.ankiesmp.dominium.core.access.AccessLevel;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessEntry;
import dev.ankiesmp.dominium.core.access.PersonalClaimAccessStore;
import dev.ankiesmp.dominium.core.access.PersonalClaimSettings;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqlPersonalClaimAccessStore implements PersonalClaimAccessStore {

    private final Database db;

    public SqlPersonalClaimAccessStore(Database db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    @Override
    public void upsert(PersonalClaimAccessEntry entry) {
        db.withTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM personal_claim_access WHERE claim_id = ? AND player_uuid = ?")) {
                del.setString(1, entry.claimId().toString());
                del.setString(2, entry.playerUuid().toString());
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO personal_claim_access(claim_id, player_uuid, level, added_at, added_by) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ins.setString(1, entry.claimId().toString());
                ins.setString(2, entry.playerUuid().toString());
                ins.setString(3, entry.level().name());
                ins.setLong(4, entry.addedAt().toEpochMilli());
                ins.setString(5, entry.addedBy());
                ins.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<AccessLevel> remove(UUID claimId, UUID playerId) {
        return db.withTransaction(conn -> {
            AccessLevel prev = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT level FROM personal_claim_access WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) prev = AccessLevel.valueOf(rs.getString(1));
                }
            }
            if (prev == null) return Optional.empty();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM personal_claim_access WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerId.toString());
                ps.executeUpdate();
            }
            return Optional.of(prev);
        });
    }

    @Override
    public Optional<AccessLevel> levelFor(UUID claimId, UUID playerId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT level FROM personal_claim_access WHERE claim_id = ? AND player_uuid = ?")) {
                ps.setString(1, claimId.toString());
                ps.setString(2, playerId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.<AccessLevel>empty();
                    return Optional.of(AccessLevel.valueOf(rs.getString(1)));
                }
            }
        });
    }

    @Override
    public List<PersonalClaimAccessEntry> listForClaim(UUID claimId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT player_uuid, level, added_at, added_by FROM personal_claim_access " +
                            "WHERE claim_id = ? ORDER BY level, added_at")) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<PersonalClaimAccessEntry> out = new ArrayList<>();
                    while (rs.next()) {
                        out.add(new PersonalClaimAccessEntry(
                                claimId,
                                UUID.fromString(rs.getString(1)),
                                AccessLevel.valueOf(rs.getString(2)),
                                Instant.ofEpochMilli(rs.getLong(3)),
                                rs.getString(4)));
                    }
                    return out;
                }
            }
        });
    }

    @Override
    public void setNoAccess(UUID claimId, boolean noAccess, long updatedAtEpochMillis) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO personal_claim_settings(claim_id, no_access, updated_at) " +
                            "VALUES (?, ?, ?) " +
                            "ON CONFLICT(claim_id) DO UPDATE SET no_access = excluded.no_access, " +
                            "updated_at = excluded.updated_at")) {
                ps.setString(1, claimId.toString());
                ps.setInt(2, noAccess ? 1 : 0);
                ps.setLong(3, updatedAtEpochMillis);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public PersonalClaimSettings settingsFor(UUID claimId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT no_access FROM personal_claim_settings WHERE claim_id = ?")) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return PersonalClaimSettings.defaults(claimId);
                    return new PersonalClaimSettings(claimId, rs.getInt(1) == 1);
                }
            }
        });
    }
}
