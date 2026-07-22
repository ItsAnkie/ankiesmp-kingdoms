package dev.ankiesmp.dominium.storage.kingdom;

import dev.ankiesmp.dominium.core.kingdom.Kingdom;
import dev.ankiesmp.dominium.core.kingdom.KingdomInvite;
import dev.ankiesmp.dominium.core.kingdom.KingdomMember;
import dev.ankiesmp.dominium.core.kingdom.KingdomRole;
import dev.ankiesmp.dominium.core.kingdom.KingdomStore;
import dev.ankiesmp.dominium.core.kingdom.KingdomVisitor;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SqlKingdomStore implements KingdomStore {

    private final Database db;

    public SqlKingdomStore(Database db) {
        this.db = Objects.requireNonNull(db);
    }

    // ---------- kingdoms ----------

    @Override
    public Optional<Kingdom> findKingdom(UUID kingdomId) {
        return db.withConnection(conn -> loadKingdom(conn, kingdomId));
    }

    @Override
    public Optional<Kingdom> findKingdomByNormalizedName(String normalized) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, display_name, normalized_name, created_at, updated_at " +
                            "FROM kingdoms WHERE normalized_name = ?")) {
                ps.setString(1, normalized);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readKingdom(rs)) : Optional.<Kingdom>empty();
                }
            }
        });
    }

    @Override
    public List<Kingdom> listKingdoms() {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, display_name, normalized_name, created_at, updated_at " +
                            "FROM kingdoms ORDER BY normalized_name");
                 ResultSet rs = ps.executeQuery()) {
                List<Kingdom> out = new ArrayList<>();
                while (rs.next()) out.add(readKingdom(rs));
                return out;
            }
        });
    }

    @Override
    public Kingdom createWithLeader(UUID kingdomId, String displayName, String normalizedName,
                                    UUID leaderUuid, Instant createdAt) {
        return db.withTransaction(conn -> {
            long now = createdAt.toEpochMilli();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdoms(id, display_name, normalized_name, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, displayName);
                ps.setString(3, normalizedName);
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdom_members(kingdom_id, player_uuid, role, joined_at, promoted_at) " +
                            "VALUES (?, ?, 'LEADER', ?, ?)")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, leaderUuid.toString());
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return new Kingdom(kingdomId, displayName, normalizedName,
                    Instant.ofEpochMilli(now), Instant.ofEpochMilli(now));
        });
    }

    @Override
    public void disband(UUID kingdomId) {
        db.withTransaction(conn -> {
            // Kingdoms delete cascades naar members / invites / visitors via FK ON DELETE CASCADE.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdoms WHERE id = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void transferLeadership(UUID kingdomId, UUID oldLeader, UUID newLeader, Instant at) {
        db.withTransaction(conn -> {
            long now = at.toEpochMilli();
            // Twee-fase swap: eerst leader → member (verwijdert unique-leader-index botsing),
            // dan newLeader → LEADER.
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE kingdom_members SET role = 'MEMBER', promoted_at = ? " +
                            "WHERE kingdom_id = ? AND player_uuid = ? AND role = 'LEADER'")) {
                ps.setLong(1, now);
                ps.setString(2, kingdomId.toString());
                ps.setString(3, oldLeader.toString());
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalStateException(
                        "old leader " + oldLeader + " is not LEADER of " + kingdomId);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE kingdom_members SET role = 'LEADER', promoted_at = ? " +
                            "WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setLong(1, now);
                ps.setString(2, kingdomId.toString());
                ps.setString(3, newLeader.toString());
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalStateException(
                        "new leader " + newLeader + " is not a member of " + kingdomId);
            }
            touchKingdom(conn, kingdomId, now);
            return null;
        });
    }

    // ---------- members ----------

    @Override
    public Optional<KingdomMember> findMembership(UUID playerUuid) {
        return db.withConnection(conn -> loadMembership(conn, playerUuid));
    }

    @Override
    public List<KingdomMember> listMembers(UUID kingdomId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT kingdom_id, player_uuid, role, joined_at, promoted_at " +
                            "FROM kingdom_members WHERE kingdom_id = ? " +
                            "ORDER BY role, joined_at")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<KingdomMember> out = new ArrayList<>();
                    while (rs.next()) out.add(readMember(rs));
                    return out;
                }
            }
        });
    }

    @Override
    public void updateRole(UUID kingdomId, UUID playerUuid, KingdomRole newRole, Instant at) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE kingdom_members SET role = ?, promoted_at = ? " +
                            "WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setString(1, newRole.name());
                ps.setLong(2, at.toEpochMilli());
                ps.setString(3, kingdomId.toString());
                ps.setString(4, playerUuid.toString());
                ps.executeUpdate();
            }
            touchKingdom(conn, kingdomId, at.toEpochMilli());
            return null;
        });
    }

    @Override
    public void removeMember(UUID kingdomId, UUID playerUuid) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_members WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            touchKingdom(conn, kingdomId, System.currentTimeMillis());
            return null;
        });
    }

    // ---------- invites ----------

    @Override
    public void deleteExpiredInvites(Instant now) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_invites WHERE expires_at <= ?")) {
                ps.setLong(1, now.toEpochMilli());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<KingdomInvite> findInvite(UUID kingdomId, UUID targetUuid) {
        return db.withConnection(conn -> loadInvite(conn, kingdomId, targetUuid));
    }

    @Override
    public List<KingdomInvite> invitesForTarget(UUID targetUuid) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, kingdom_id, target_uuid, inviter_uuid, created_at, expires_at " +
                            "FROM kingdom_invites WHERE target_uuid = ? ORDER BY created_at")) {
                ps.setString(1, targetUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<KingdomInvite> out = new ArrayList<>();
                    while (rs.next()) out.add(readInvite(rs));
                    return out;
                }
            }
        });
    }

    @Override
    public void insertInvite(UUID kingdomId, UUID targetUuid, UUID inviterUuid,
                             Instant createdAt, Instant expiresAt) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdom_invites(kingdom_id, target_uuid, inviter_uuid, created_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, targetUuid.toString());
                ps.setString(3, inviterUuid.toString());
                ps.setLong(4, createdAt.toEpochMilli());
                ps.setLong(5, expiresAt.toEpochMilli());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteInvite(UUID kingdomId, UUID targetUuid) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_invites WHERE kingdom_id = ? AND target_uuid = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, targetUuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<KingdomMember> acceptInvite(UUID kingdomId, UUID targetUuid, Instant now) {
        return db.withTransaction(conn -> {
            // 1) invite bestaat + niet expired?
            Optional<KingdomInvite> invite = loadInvite(conn, kingdomId, targetUuid);
            if (invite.isEmpty() || invite.get().isExpired(now)) return Optional.<KingdomMember>empty();
            // 2) speler nog niet lid van ander kingdom?
            Optional<KingdomMember> existing = loadMembership(conn, targetUuid);
            if (existing.isPresent()) return Optional.<KingdomMember>empty();
            // 3) verwijder eventuele visitor entry voor deze kingdom.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_visitors WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, targetUuid.toString());
                ps.executeUpdate();
            }
            // 4) insert member als MEMBER.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdom_members(kingdom_id, player_uuid, role, joined_at, promoted_at) " +
                            "VALUES (?, ?, 'MEMBER', ?, NULL)")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, targetUuid.toString());
                ps.setLong(3, now.toEpochMilli());
                ps.executeUpdate();
            }
            // 5) verwijder ALLE openstaande invites voor deze target
            //    (accepteren = definitieve keuze; andere invites vervallen).
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_invites WHERE target_uuid = ?")) {
                ps.setString(1, targetUuid.toString());
                ps.executeUpdate();
            }
            touchKingdom(conn, kingdomId, now.toEpochMilli());
            return Optional.of(new KingdomMember(kingdomId, targetUuid, KingdomRole.MEMBER,
                    now, Optional.empty()));
        });
    }

    // ---------- visitors ----------

    @Override
    public List<KingdomVisitor> listVisitors(UUID kingdomId) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT kingdom_id, player_uuid, added_by, created_at " +
                            "FROM kingdom_visitors WHERE kingdom_id = ? ORDER BY created_at")) {
                ps.setString(1, kingdomId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<KingdomVisitor> out = new ArrayList<>();
                    while (rs.next()) out.add(new KingdomVisitor(
                            UUID.fromString(rs.getString(1)),
                            UUID.fromString(rs.getString(2)),
                            UUID.fromString(rs.getString(3)),
                            Instant.ofEpochMilli(rs.getLong(4))));
                    return out;
                }
            }
        });
    }

    @Override
    public boolean isVisitor(UUID kingdomId, UUID playerUuid) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM kingdom_visitors WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    @Override
    public void insertVisitor(UUID kingdomId, UUID playerUuid, UUID addedBy, Instant at) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO kingdom_visitors(kingdom_id, player_uuid, added_by, created_at) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, playerUuid.toString());
                ps.setString(3, addedBy.toString());
                ps.setLong(4, at.toEpochMilli());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void removeVisitor(UUID kingdomId, UUID playerUuid) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM kingdom_visitors WHERE kingdom_id = ? AND player_uuid = ?")) {
                ps.setString(1, kingdomId.toString());
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ---------- helpers ----------

    private static void touchKingdom(Connection conn, UUID kingdomId, long now) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE kingdoms SET updated_at = ? WHERE id = ?")) {
            ps.setLong(1, now);
            ps.setString(2, kingdomId.toString());
            ps.executeUpdate();
        }
    }

    private static Optional<Kingdom> loadKingdom(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, display_name, normalized_name, created_at, updated_at " +
                        "FROM kingdoms WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readKingdom(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<KingdomInvite> loadInvite(Connection conn, UUID kingdomId, UUID target)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, kingdom_id, target_uuid, inviter_uuid, created_at, expires_at " +
                        "FROM kingdom_invites WHERE kingdom_id = ? AND target_uuid = ?")) {
            ps.setString(1, kingdomId.toString());
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readInvite(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<KingdomMember> loadMembership(Connection conn, UUID player)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT kingdom_id, player_uuid, role, joined_at, promoted_at " +
                        "FROM kingdom_members WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readMember(rs)) : Optional.empty();
            }
        }
    }

    private static Kingdom readKingdom(ResultSet rs) throws SQLException {
        return new Kingdom(
                UUID.fromString(rs.getString(1)),
                rs.getString(2),
                rs.getString(3),
                Instant.ofEpochMilli(rs.getLong(4)),
                Instant.ofEpochMilli(rs.getLong(5)));
    }

    private static KingdomMember readMember(ResultSet rs) throws SQLException {
        long promotedAt = rs.getLong(5);
        Optional<Instant> promoted = rs.wasNull() ? Optional.empty()
                : Optional.of(Instant.ofEpochMilli(promotedAt));
        return new KingdomMember(
                UUID.fromString(rs.getString(1)),
                UUID.fromString(rs.getString(2)),
                KingdomRole.valueOf(rs.getString(3)),
                Instant.ofEpochMilli(rs.getLong(4)),
                promoted);
    }

    private static KingdomInvite readInvite(ResultSet rs) throws SQLException {
        return new KingdomInvite(
                rs.getLong(1),
                UUID.fromString(rs.getString(2)),
                UUID.fromString(rs.getString(3)),
                UUID.fromString(rs.getString(4)),
                Instant.ofEpochMilli(rs.getLong(5)),
                Instant.ofEpochMilli(rs.getLong(6)));
    }

    // silence unused import warnings when jdk complains
    @SuppressWarnings("unused")
    private static void _typesGuard() { int _t = Types.VARCHAR; Statement.class.getName(); }
}
