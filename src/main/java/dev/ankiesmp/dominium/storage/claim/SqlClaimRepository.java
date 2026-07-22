package dev.ankiesmp.dominium.storage.claim;

import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.claim.ClaimGeometry;
import dev.ankiesmp.dominium.core.claim.ClaimOwner;
import dev.ankiesmp.dominium.core.claim.ClaimRectangle;
import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.core.common.WorldRef;
import dev.ankiesmp.dominium.storage.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC-repository voor top-level {@link Claim}-records + hun regions
 * ({@code claim_regions}). Sinds fase 5 is {@code claim_regions} de
 * authoritative geometry-source; {@code claims.min_x..max_z} zijn cached
 * bounds die op iedere geometry-mutatie worden bijgewerkt.
 */
public final class SqlClaimRepository {

    private final Database database;

    public SqlClaimRepository(Database database) {
        this.database = database;
    }

    /** Insert claim + één rij per {@link ClaimGeometry#regions()} + CREATE-revision. */
    public void insert(Claim claim) {
        database.withTransaction(conn -> {
            insertClaim(conn, claim);
            for (ClaimRectangle r : claim.geometry().regions()) {
                insertRegion(conn, claim.id(), r, claim.createdAt().toEpochMilli());
            }
            insertRevision(conn, claim.id(), claim.rect(), "CREATE", claim.geometry().area());
            return null;
        });
    }

    /** Vervangt de geometry volledig: alle regions herschrijven + bounds bijwerken. */
    public void replaceGeometry(UUID claimId, ClaimGeometry oldGeometry,
                                ClaimGeometry newGeometry, String reason) {
        long delta = newGeometry.area() - oldGeometry.area();
        database.withTransaction(conn -> {
            ClaimRectangle b = newGeometry.bounds();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE claims SET min_x=?, min_z=?, max_x=?, max_z=? WHERE id=?")) {
                ps.setInt(1, b.minX());
                ps.setInt(2, b.minZ());
                ps.setInt(3, b.maxX());
                ps.setInt(4, b.maxZ());
                ps.setString(5, claimId.toString());
                if (ps.executeUpdate() == 0) {
                    throw new IllegalStateException("claim " + claimId + " not found for geometry update");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM claim_regions WHERE claim_id = ?")) {
                ps.setString(1, claimId.toString());
                ps.executeUpdate();
            }
            long now = System.currentTimeMillis();
            for (ClaimRectangle r : newGeometry.regions()) {
                insertRegion(conn, claimId, r, now);
            }
            insertRevision(conn, claimId, newGeometry.bounds(), reason, delta);
            return null;
        });
    }

    /** Legacy single-rectangle resize (backwards-compat pad). */
    public void updateRectangle(UUID claimId, ClaimRectangle oldRect, ClaimRectangle newRect) {
        replaceGeometry(claimId,
                ClaimGeometry.ofRectangle(oldRect),
                ClaimGeometry.ofRectangle(newRect), "RESIZE");
    }

    public void delete(UUID claimId) {
        database.withTransaction(conn -> {
            deleteInConnection(conn, claimId);
            return null;
        });
    }

    /** Delete binnen een reeds actieve Connection (voor atomic multi-op tx). */
    public void deleteInConnection(Connection conn, UUID claimId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claims WHERE id=?")) {
            ps.setString(1, claimId.toString());
            ps.executeUpdate();
        }
    }

    /** Vervangt de geometry binnen een reeds actieve Connection (atomair pad). */
    public void replaceGeometryInConnection(Connection conn, UUID claimId,
                                            ClaimGeometry oldGeometry, ClaimGeometry newGeometry,
                                            String reason) throws SQLException {
        long delta = newGeometry.area() - oldGeometry.area();
        ClaimRectangle b = newGeometry.bounds();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE claims SET min_x=?, min_z=?, max_x=?, max_z=? WHERE id=?")) {
            ps.setInt(1, b.minX());
            ps.setInt(2, b.minZ());
            ps.setInt(3, b.maxX());
            ps.setInt(4, b.maxZ());
            ps.setString(5, claimId.toString());
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("claim " + claimId + " not found");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM claim_regions WHERE claim_id = ?")) {
            ps.setString(1, claimId.toString());
            ps.executeUpdate();
        }
        long now = System.currentTimeMillis();
        for (ClaimRectangle r : newGeometry.regions()) {
            insertRegion(conn, claimId, r, now);
        }
        insertRevision(conn, claimId, newGeometry.bounds(), reason, delta);
    }

    public Optional<Claim> findById(UUID claimId) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, world_id, owner_type, owner_id, min_x, min_z, max_x, max_z, created_at " +
                            "FROM claims WHERE id=?")) {
                ps.setString(1, claimId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.<Claim>empty();
                    Claim stub = rowToClaimWithBounds(rs);
                    List<ClaimRectangle> regs = loadRegions(conn, stub.id());
                    ClaimGeometry g = regs.isEmpty()
                            ? ClaimGeometry.ofRectangle(stub.rect())
                            : ClaimGeometry.ofRegions(regs);
                    return Optional.of(new Claim(stub.id(), stub.world(), g,
                            stub.owner(), stub.createdAt()));
                }
            }
        });
    }

    public List<Claim> loadAll() {
        return database.withConnection(conn -> {
            List<Claim> stubs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, world_id, owner_type, owner_id, min_x, min_z, max_x, max_z, created_at " +
                            "FROM claims ORDER BY created_at ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) stubs.add(rowToClaimWithBounds(rs));
            }
            Map<UUID, List<ClaimRectangle>> regionsByClaim = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT claim_id, min_x, min_z, max_x, max_z FROM claim_regions");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID cid = UUID.fromString(rs.getString(1));
                    regionsByClaim.computeIfAbsent(cid, k -> new ArrayList<>())
                            .add(new ClaimRectangle(rs.getInt(2), rs.getInt(3),
                                    rs.getInt(4), rs.getInt(5)));
                }
            }
            List<Claim> out = new ArrayList<>(stubs.size());
            for (Claim stub : stubs) {
                var regs = regionsByClaim.get(stub.id());
                ClaimGeometry g = (regs == null || regs.isEmpty())
                        ? ClaimGeometry.ofRectangle(stub.rect())
                        : ClaimGeometry.ofRegions(regs);
                out.add(new Claim(stub.id(), stub.world(), g, stub.owner(), stub.createdAt()));
            }
            return out;
        });
    }

    private static List<ClaimRectangle> loadRegions(Connection conn, UUID claimId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT min_x, min_z, max_x, max_z FROM claim_regions WHERE claim_id = ?")) {
            ps.setString(1, claimId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<ClaimRectangle> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new ClaimRectangle(rs.getInt(1), rs.getInt(2),
                            rs.getInt(3), rs.getInt(4)));
                }
                return out;
            }
        }
    }

    private static Claim rowToClaimWithBounds(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString(1));
        WorldRef world = new WorldRef(UUID.fromString(rs.getString(2)));
        ClaimType type = ClaimType.valueOf(rs.getString(3));
        UUID ownerId = UUID.fromString(rs.getString(4));
        ClaimOwner owner = switch (type) {
            case PERSONAL -> ClaimOwner.personal(ownerId);
            case KINGDOM  -> ClaimOwner.kingdom(ownerId);
            case ADMIN    -> ClaimOwner.admin(ownerId);
        };
        ClaimRectangle rect = new ClaimRectangle(rs.getInt(5), rs.getInt(6),
                rs.getInt(7), rs.getInt(8));
        Instant created = Instant.ofEpochMilli(rs.getLong(9));
        return new Claim(id, world, rect, owner, created);
    }

    private static void insertClaim(Connection conn, Claim claim) throws SQLException {
        ClaimRectangle b = claim.geometry().bounds();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claims(id, world_id, owner_type, owner_id, min_x, min_z, max_x, max_z, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, claim.id().toString());
            ps.setString(2, claim.world().id().toString());
            ps.setString(3, claim.owner().type().name());
            ps.setString(4, claim.owner().id().toString());
            ps.setInt(5, b.minX());
            ps.setInt(6, b.minZ());
            ps.setInt(7, b.maxX());
            ps.setInt(8, b.maxZ());
            ps.setLong(9, claim.createdAt().toEpochMilli());
            ps.executeUpdate();
        }
    }

    private static void insertRegion(Connection conn, UUID claimId, ClaimRectangle rect, long now)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_regions(claim_id, min_x, min_z, max_x, max_z, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, claimId.toString());
            ps.setInt(2, rect.minX());
            ps.setInt(3, rect.minZ());
            ps.setInt(4, rect.maxX());
            ps.setInt(5, rect.maxZ());
            ps.setLong(6, now);
            ps.executeUpdate();
        }
    }

    private static void insertRevision(Connection conn, UUID claimId, ClaimRectangle rect,
                                       String reason, long costDelta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO claim_geometry_revisions(claim_id, min_x, min_z, max_x, max_z, reason, cost_delta, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, claimId.toString());
            ps.setInt(2, rect.minX());
            ps.setInt(3, rect.minZ());
            ps.setInt(4, rect.maxX());
            ps.setInt(5, rect.maxZ());
            ps.setString(6, reason);
            ps.setLong(7, costDelta);
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
