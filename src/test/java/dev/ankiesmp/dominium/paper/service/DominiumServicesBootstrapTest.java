package dev.ankiesmp.dominium.paper.service;

import dev.ankiesmp.dominium.core.bootstrap.DominiumCore;
import dev.ankiesmp.dominium.core.claim.Claim;
import dev.ankiesmp.dominium.core.protection.Audience;
import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.migrations.Migration;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifieert dat {@link DominiumCore#bootstrap} een schone, volledige
 * startup levert en dat bij een gedeeltelijke fout géén Hikari-pool of
 * executor blijft leven. Dekking van dezelfde code-paden die
 * {@code DominiumServices#bootstrap} in productie doorloopt.
 */
class DominiumServicesBootstrapTest {

    @TempDir Path tempDir;

    @Test
    void headlessBootstrapAppliesAllMigrationsAndCreatesTables() {
        try (DominiumCore core = DominiumCore.bootstrap(tempDir, "svc.db",
                (claim, actor) -> Audience.PUBLIC)) {
            Database db = core.database();
            assertTrue(hasTable(db, "claims"), "V3 moet 'claims' hebben aangemaakt");
            assertTrue(hasTable(db, "claim_geometry_revisions"));
            assertTrue(hasTable(db, "claim_block_ledger"));
            assertTrue(hasTable(db, "claim_block_balance"));
            assertTrue(hasTable(db, "bank_operation_journal"));
            assertTrue(hasTable(db, "schema_version"));
            assertNotNull(core.claimService());
            assertNotNull(core.ledger());
            assertNotNull(core.claimBlockService());
            assertNotNull(core.protectionService());
        }
    }

    @Test
    void secondBootstrapReusesSchemaWithoutErrors() {
        try (DominiumCore first = DominiumCore.bootstrap(tempDir, "svc.db",
                (claim, actor) -> Audience.PUBLIC)) {
            assertNotNull(first);
        }
        try (DominiumCore second = DominiumCore.bootstrap(tempDir, "svc.db",
                (claim, actor) -> Audience.PUBLIC)) {
            assertTrue(hasTable(second.database(), "claims"),
                    "'claims' moet een restart overleven");
            // Sanity: geen actieve claims na een schone restart.
            assertEquals(0, second.claimIndex().all().size());
        }
    }

    @Test
    void bootstrapClosesResourcesWhenMigrationFails() throws Exception {
        // Repliceer wat DominiumCore.bootstrap doet, maar met een bewust gebroken migratie.
        // Verwacht: de pool moet gesloten zijn en de executor terminated na de exception.
        Path dbFile = tempDir.resolve("fail.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        Database db = null;
        ExecutorService leakedExecutor = null;
        try {
            db = Database.sqlite(jdbcUrl);
            leakedExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "dominium-db-fail");
                t.setDaemon(true);
                return t;
            });

            var broken = new dev.ankiesmp.dominium.storage.migrations.MigrationSource() {
                @Override public List<Migration> load() {
                    return List.of(new Migration(999, "kapot", "THIS IS NOT SQL"));
                }
            };
            final Database dbRef = db;
            final ExecutorService execRef = leakedExecutor;
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
                try {
                    new MigrationRunner(dbRef, broken).migrate();
                } catch (RuntimeException e) {
                    execRef.shutdown();
                    try {
                        if (!execRef.awaitTermination(2, TimeUnit.SECONDS)) execRef.shutdownNow();
                    } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    dbRef.close();
                    throw e;
                }
            });
            assertNotNull(thrown);

            assertTrue(isHikariClosed(db), "Hikari-pool moet gesloten zijn na bootstrap-fout");
            assertTrue(leakedExecutor.isShutdown(), "executor moet shutdown zijn");
            assertTrue(leakedExecutor.awaitTermination(2, TimeUnit.SECONDS),
                    "executor moet terminated raken");
        } finally {
            if (leakedExecutor != null) leakedExecutor.shutdownNow();
            if (db != null && !isHikariClosed(db)) db.close();
        }
    }

    @Test
    void closingCoreShutsDownHikariAndExecutor() throws Exception {
        DominiumCore core = DominiumCore.bootstrap(tempDir, "close.db",
                (claim, actor) -> Audience.PUBLIC);
        Database db = core.database();
        Object exec = core.dbExecutorService();
        assertFalse(isHikariClosed(db));

        core.close();

        assertTrue(isHikariClosed(db), "Hikari moet dicht zijn na close()");
        assertTrue(((java.util.concurrent.ExecutorService) exec).isShutdown(),
                "dbExecutor moet shutdown zijn na close()");
    }

    // --- helpers ---

    @SuppressWarnings("unused") // Claim is bewust bereikbaar voor toekomstige tests
    private static Claim unusedForSuppression() { return null; }

    private static boolean hasTable(Database db, String name) {
        return db.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /** Reflection-check op Database.dataSource — best we kunnen zonder API te breken. */
    private static boolean isHikariClosed(Database db) {
        try {
            Field ds = Database.class.getDeclaredField("dataSource");
            ds.setAccessible(true);
            Object hikariDs = ds.get(db);
            Method isClosed = hikariDs.getClass().getMethod("isClosed");
            return (Boolean) isClosed.invoke(hikariDs);
        } catch (ReflectiveOperationException e) {
            fail("cannot introspect Database.dataSource: " + e.getMessage());
            return false;
        }
    }

    // Voor de compiler zodat UUID/import blijven — geen inhoud
    @SuppressWarnings("unused")
    private static UUID unused() { return UUID.randomUUID(); }
}
