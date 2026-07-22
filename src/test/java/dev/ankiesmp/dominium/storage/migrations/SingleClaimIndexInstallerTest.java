package dev.ankiesmp.dominium.storage.migrations;

import dev.ankiesmp.dominium.core.claim.ClaimType;
import dev.ankiesmp.dominium.storage.db.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressie voor de V6 upgrade-bug: bestaande databases met duplicaten
 * mogen NIET stil crashen tijdens bootstrap.
 */
class SingleClaimIndexInstallerTest {

    @TempDir Path tempDir;
    private Database db;

    @BeforeEach
    void setUp() {
        db = Database.sqlite("jdbc:sqlite:" + tempDir.resolve("v6.db").toAbsolutePath());
        new MigrationRunner(db).migrate(); // V1..V5, geen V6
    }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    private void insertClaim(UUID id, ClaimType type, UUID ownerId, int minX, int minZ,
                             int maxX, int maxZ) {
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO claims(id, world_id, owner_type, owner_id, " +
                            "min_x, min_z, max_x, max_z, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, UUID.randomUUID().toString());
                ps.setString(3, type.name());
                ps.setString(4, ownerId.toString());
                ps.setInt(5, minX);
                ps.setInt(6, minZ);
                ps.setInt(7, maxX);
                ps.setInt(8, maxZ);
                ps.setLong(9, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    private boolean indexExists() {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type='index' AND name='idx_claims_unique_owner'");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        });
    }

    private boolean schemaVersionHas(int v) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM schema_version WHERE version = ?")) {
                ps.setInt(1, v);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    // V6-001 — schone DB zonder claims → APPLIED.
    @Test
    void cleanDatabaseInstalls() {
        var r = SingleClaimIndexInstaller.install(db);
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED, r.kind());
        assertTrue(indexExists());
        assertTrue(schemaVersionHas(6));
    }

    // V6-002 — één claim per owner → APPLIED.
    @Test
    void singleClaimPerOwnerInstalls() {
        insertClaim(UUID.randomUUID(), ClaimType.PERSONAL, UUID.randomUUID(), 0, 0, 9, 9);
        insertClaim(UUID.randomUUID(), ClaimType.KINGDOM,  UUID.randomUUID(), 100, 100, 109, 109);
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED,
                SingleClaimIndexInstaller.install(db).kind());
        assertTrue(indexExists());
    }

    // V6-003 — twee PERSONAL-claims voor dezelfde speler → DEFERRED, geen dataverlies.
    @Test
    void duplicatePersonalClaimsDefer() {
        UUID owner = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        insertClaim(c1, ClaimType.PERSONAL, owner, 0, 0, 9, 9);
        insertClaim(c2, ClaimType.PERSONAL, owner, 100, 100, 109, 109);
        var r = SingleClaimIndexInstaller.install(db);
        assertEquals(SingleClaimIndexInstaller.Kind.DEFERRED, r.kind());
        assertEquals(1, r.conflicts().size());
        var conflict = r.conflicts().get(0);
        assertEquals(ClaimType.PERSONAL, conflict.type());
        assertEquals(owner, conflict.ownerId());
        assertEquals(2, conflict.claims().size());
        assertFalse(indexExists(), "index mag niet geplaatst zijn bij conflicten");
        assertFalse(schemaVersionHas(6), "V6 mag niet als toegepast gemarkeerd zijn");
        // Data staat er nog steeds.
        assertEquals(2, countClaimsForOwner(owner));
    }

    // V6-004 — twee KINGDOM-claims voor hetzelfde kingdom → DEFERRED.
    @Test
    void duplicateKingdomClaimsDefer() {
        UUID kingdomId = UUID.randomUUID();
        insertClaim(UUID.randomUUID(), ClaimType.KINGDOM, kingdomId, 0, 0, 9, 9);
        insertClaim(UUID.randomUUID(), ClaimType.KINGDOM, kingdomId, 100, 100, 109, 109);
        var r = SingleClaimIndexInstaller.install(db);
        assertEquals(SingleClaimIndexInstaller.Kind.DEFERRED, r.kind());
        assertEquals(1, r.conflicts().size());
        assertEquals(ClaimType.KINGDOM, r.conflicts().get(0).type());
    }

    // V6-005 — na repair (delete duplicaat) slaagt een tweede install.
    @Test
    void afterRepairInstallSucceeds() {
        UUID owner = UUID.randomUUID();
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        insertClaim(c1, ClaimType.PERSONAL, owner, 0, 0, 9, 9);
        insertClaim(c2, ClaimType.PERSONAL, owner, 100, 100, 109, 109);
        assertEquals(SingleClaimIndexInstaller.Kind.DEFERRED,
                SingleClaimIndexInstaller.install(db).kind());
        // Admin lost handmatig één op.
        db.withTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM claims WHERE id = ?")) {
                ps.setString(1, c2.toString());
                ps.executeUpdate();
            }
            return null;
        });
        // Volgende bootstrap slaagt.
        assertEquals(SingleClaimIndexInstaller.Kind.APPLIED,
                SingleClaimIndexInstaller.install(db).kind());
        assertTrue(indexExists());
        assertTrue(schemaVersionHas(6));
    }

    // V6-006 — na APPLIED is de tweede aanroep ALREADY_APPLIED (idempotent).
    @Test
    void secondInstallReturnsAlreadyApplied() {
        SingleClaimIndexInstaller.install(db);
        assertEquals(SingleClaimIndexInstaller.Kind.ALREADY_APPLIED,
                SingleClaimIndexInstaller.install(db).kind());
    }

    // V6-007 — één conflict blokkeert alleen die owner; findConflicts noemt hem exact.
    @Test
    void otherOwnersNotFlagged() {
        UUID conflicted = UUID.randomUUID();
        UUID clean1 = UUID.randomUUID();
        UUID clean2 = UUID.randomUUID();
        insertClaim(UUID.randomUUID(), ClaimType.PERSONAL, conflicted, 0, 0, 9, 9);
        insertClaim(UUID.randomUUID(), ClaimType.PERSONAL, conflicted, 100, 100, 109, 109);
        insertClaim(UUID.randomUUID(), ClaimType.PERSONAL, clean1, 200, 200, 209, 209);
        insertClaim(UUID.randomUUID(), ClaimType.KINGDOM,  clean2, 300, 300, 309, 309);
        var conflicts = SingleClaimIndexInstaller.findConflicts(db);
        assertEquals(1, conflicts.size());
        assertEquals(conflicted, conflicts.get(0).ownerId());
    }

    private long countClaimsForOwner(UUID owner) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM claims WHERE owner_id = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next(); return rs.getLong(1);
                }
            }
        });
    }
}
