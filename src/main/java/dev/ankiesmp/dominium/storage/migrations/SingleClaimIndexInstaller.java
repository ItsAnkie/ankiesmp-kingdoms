package dev.ankiesmp.dominium.storage.migrations;

import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Installeert de "één claim per PERSONAL/KINGDOM owner"-unique index
 * (was V6) alléén als er geen duplicate owners in {@code claims} bestaan.
 *
 * <p>Waarom niet als gewone {@link Migration}: bestaande databases uit
 * oudere builds kunnen legitieme duplicaten hebben (bug uit fase 3/4).
 * Een blinde {@code CREATE UNIQUE INDEX} faalt dan met SQLITE_CONSTRAINT_UNIQUE
 * en de plugin crasht vóór welke admin-tooling dan ook beschikbaar is.
 *
 * <p>Deze installer:
 * <ol>
 *   <li>controleert of de index al staat (schema_version.version = 6) → ALREADY_APPLIED;</li>
 *   <li>zoekt duplicaten via één GROUP BY-query;</li>
 *   <li>bij nul duplicaten: {@code CREATE UNIQUE INDEX} + INSERT in
 *       {@code schema_version} binnen één transactie → APPLIED;</li>
 *   <li>bij duplicaten: geen wijzigingen; retourneert DEFERRED met de
 *       conflictlijst zodat de admin die kan opschonen.</li>
 * </ol>
 */
public final class SingleClaimIndexInstaller {

    public static final int VERSION = 6;
    public static final String DESCRIPTION = "single claim per owner";
    public static final String INDEX_NAME = "idx_claims_unique_owner";
    private static final String CREATE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS " + INDEX_NAME
                    + " ON claims(owner_type, owner_id) "
                    + "WHERE owner_type IN ('PERSONAL', 'KINGDOM')";

    private SingleClaimIndexInstaller() {}

    public static Result install(Database db) {
        // 1. Al toegepast?
        boolean alreadyApplied = db.withConnection(conn -> {
            if (!hasSchemaVersionTable(conn)) return false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM schema_version WHERE version = ?")) {
                ps.setInt(1, VERSION);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
        if (alreadyApplied) return Result.alreadyApplied();

        // 2. Duplicaten?
        List<OwnerConflict> conflicts = findConflicts(db);
        if (!conflicts.isEmpty()) return Result.deferred(conflicts);

        // 3. Atomair aanmaken + registreren.
        db.withTransaction(conn -> {
            try (PreparedStatement create = conn.prepareStatement(CREATE_INDEX_SQL)) {
                create.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO schema_version(version, applied_at, description) VALUES (?, ?, ?)")) {
                ins.setInt(1, VERSION);
                ins.setLong(2, System.currentTimeMillis());
                ins.setString(3, DESCRIPTION);
                ins.executeUpdate();
            }
            return null;
        });
        return Result.applied();
    }

    /** Groepeert claims per owner en levert alleen groepen met &gt;1 rij op. */
    public static List<OwnerConflict> findConflicts(Database db) {
        return db.withConnection(conn -> {
            List<OwnerConflict> out = new ArrayList<>();
            try (PreparedStatement group = conn.prepareStatement(
                    "SELECT owner_type, owner_id, COUNT(*) AS n FROM claims " +
                            "WHERE owner_type IN ('PERSONAL', 'KINGDOM') " +
                            "GROUP BY owner_type, owner_id HAVING COUNT(*) > 1")) {
                try (ResultSet rs = group.executeQuery()) {
                    while (rs.next()) {
                        ClaimType type = ClaimType.valueOf(rs.getString("owner_type"));
                        UUID ownerId = UUID.fromString(rs.getString("owner_id"));
                        List<ClaimDetail> claims = loadClaimDetails(conn, type, ownerId);
                        out.add(new OwnerConflict(type, ownerId, claims));
                    }
                }
            }
            return out;
        });
    }

    private static List<ClaimDetail> loadClaimDetails(
            java.sql.Connection conn, ClaimType type, UUID ownerId) throws java.sql.SQLException {
        List<ClaimDetail> claims = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, world_id, min_x, min_z, max_x, max_z FROM claims " +
                        "WHERE owner_type = ? AND owner_id = ?")) {
            ps.setString(1, type.name());
            ps.setString(2, ownerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    claims.add(new ClaimDetail(
                            UUID.fromString(rs.getString(1)),
                            UUID.fromString(rs.getString(2)),
                            rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
                }
            }
        }
        return claims;
    }

    private static boolean hasSchemaVersionTable(java.sql.Connection conn)
            throws java.sql.SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='schema_version'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    public enum Kind { APPLIED, ALREADY_APPLIED, DEFERRED }

    public record Result(Kind kind, List<OwnerConflict> conflicts) {
        public static Result applied() { return new Result(Kind.APPLIED, List.of()); }
        public static Result alreadyApplied() { return new Result(Kind.ALREADY_APPLIED, List.of()); }
        public static Result deferred(List<OwnerConflict> conflicts) {
            return new Result(Kind.DEFERRED, List.copyOf(conflicts));
        }
        public boolean installed() { return kind == Kind.APPLIED || kind == Kind.ALREADY_APPLIED; }
        public boolean deferred()  { return kind == Kind.DEFERRED; }
    }

    public record OwnerConflict(ClaimType type, UUID ownerId, List<ClaimDetail> claims) {}
    public record ClaimDetail(UUID id, UUID worldId, int minX, int minZ, int maxX, int maxZ) {}
}
