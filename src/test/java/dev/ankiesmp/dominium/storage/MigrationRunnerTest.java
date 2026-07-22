package dev.ankiesmp.dominium.storage;

import dev.ankiesmp.dominium.storage.db.Database;
import dev.ankiesmp.dominium.storage.migrations.ClasspathMigrationSource;
import dev.ankiesmp.dominium.storage.migrations.Migration;
import dev.ankiesmp.dominium.storage.migrations.MigrationRegistry;
import dev.ankiesmp.dominium.storage.migrations.MigrationRunner;
import dev.ankiesmp.dominium.storage.migrations.MigrationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class MigrationRunnerTest {

    @TempDir Path tempDir;
    private Database database;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("mig.db");
        database = Database.sqlite("jdbc:sqlite:" + dbFile.toAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (database != null) database.close();
    }

    // ============================================================
    //  BASISDEKKING — lege DB krijgt V1 + V2 + V3, tweede run doet niets
    // ============================================================

    // DB-001
    @Test
    void firstRunAppliesAllRegisteredMigrations() {
        int applied = new MigrationRunner(database).migrate();
        assertEquals(MigrationRegistry.RESOURCE_PATHS.size(), applied,
                "aantal toegepaste migraties moet gelijk zijn aan de registry");
        assertTrue(tableExists("schema_version"));
        assertTrue(tableExists("claim_block_ledger"));
        assertTrue(tableExists("claim_block_balance"));
        assertTrue(tableExists("bank_operation_journal"));
        assertTrue(tableExists("claims"), "V3 moet 'claims' hebben aangemaakt");
        assertTrue(tableExists("claim_geometry_revisions"),
                "V3 moet claim_geometry_revisions hebben aangemaakt");
        assertTrue(tableExists("personal_claim_access"), "V4");
        assertTrue(tableExists("personal_claim_settings"), "V4");
        assertTrue(tableExists("player_activity_state"), "V4");
        assertTrue(tableExists("player_earning_state"), "V4");
        assertTrue(tableExists("kingdoms"), "V5");
        assertTrue(tableExists("kingdom_members"), "V5");
        assertTrue(tableExists("kingdom_invites"), "V5");
        assertTrue(tableExists("kingdom_visitors"), "V5");
        assertTrue(tableExists("kingdom_bank"), "V7");
        assertTrue(tableExists("claim_regions"), "V7");
        // V6 index wordt NIET meer door MigrationRunner geplaatst — dat gebeurt
        // door SingleClaimIndexInstaller met preflight; getest in
        // SingleClaimIndexInstallerTest.
    }

    private boolean indexExists(String name) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type='index' AND name=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    // DB-002
    @Test
    void secondRunAppliesNothing() {
        new MigrationRunner(database).migrate();
        int second = new MigrationRunner(database).migrate();
        assertEquals(0, second, "geen migraties zouden opnieuw moeten draaien");
    }

    // ============================================================
    //  PARTIAL-STATE UPGRADES
    // ============================================================

    // DB-003 — bestaande DB met alleen V1 → V2 en V3 komen bij
    @Test
    void onlyV1AppliedThenV2AndV3RunOnRestart() {
        MigrationSource onlyV1 = () -> loadOnly(1);
        new MigrationRunner(database, onlyV1).migrate();
        assertTrue(tableExists("claim_block_ledger"));
        assertFalse(tableExists("bank_operation_journal"));
        assertFalse(tableExists("claims"));

        int applied = new MigrationRunner(database).migrate();
        assertEquals(MigrationRegistry.RESOURCE_PATHS.size() - 1, applied,
                "alle overige migraties komen bij");
        assertTrue(tableExists("bank_operation_journal"));
        assertTrue(tableExists("claims"));
        assertTrue(tableExists("personal_claim_access"));
        assertTrue(tableExists("kingdoms"));
    }

    // DB-004 — bestaande DB met V1 en V2 → V3 en later komen bij
    @Test
    void v1AndV2AppliedThenV3RunsOnRestart() {
        MigrationSource v1v2 = () -> loadUpTo(2);
        new MigrationRunner(database, v1v2).migrate();
        assertTrue(tableExists("claim_block_ledger"));
        assertTrue(tableExists("bank_operation_journal"));
        assertFalse(tableExists("claims"));

        int applied = new MigrationRunner(database).migrate();
        assertEquals(MigrationRegistry.RESOURCE_PATHS.size() - 2, applied,
                "alle migraties vanaf V3 komen bij");
        assertTrue(tableExists("claims"));
        assertTrue(tableExists("personal_claim_access"));
        assertTrue(tableExists("kingdoms"));
    }

    // ============================================================
    //  FOUTAFHANDELING
    // ============================================================

    // DB-005 — mislukte SQL rolt terug en registreert geen versie
    @Test
    void failedMigrationRollsBackAndDoesNotRegisterVersion() {
        MigrationSource broken = () -> List.of(
                new Migration(99, "broken migration", "THIS IS NOT VALID SQL")
        );
        assertThrows(RuntimeException.class,
                () -> new MigrationRunner(database, broken).migrate());
        assertFalse(schemaVersionContains(99),
                "kapotte migratie mag geen schema_version-rij achterlaten");
        assertFalse(tableExists("something_that_wont_exist"));
    }

    // DB-006 — ontbrekend resource op de classpath → hard fail
    @Test
    void missingResourceThrowsAtLoad() {
        MigrationSource missing = new ClasspathMigrationSource(
                Thread.currentThread().getContextClassLoader(),
                List.of("db/migrations/V999__does_not_exist.sql"));
        assertThrows(IllegalStateException.class, missing::load);
    }

    // DB-007 — dubbele versie in registry → hard fail
    @Test
    void duplicateVersionInRegistryThrows() {
        MigrationSource dup = new ClasspathMigrationSource(
                Thread.currentThread().getContextClassLoader(),
                List.of(
                        "db/migrations/V1__initial_claim_block_ledger.sql",
                        "db/migrations/V1__initial_claim_block_ledger.sql"
                ));
        assertThrows(IllegalStateException.class, dup::load);
    }

    // ============================================================
    //  JAR-CLASSLOADER (simuleert Paper's shaded plugin-jar)
    // ============================================================

    // DB-008 — laden uit een echte jar via URLClassLoader
    @Test
    void loadsMigrationsFromJarClassLoader() throws Exception {
        Path jar = writeMigrationsJar(tempDir.resolve("dominium-fake.jar"));
        try (URLClassLoader jarLoader = new URLClassLoader(
                new URL[]{ jar.toUri().toURL() },
                // GEEN parent: garandeert dat de resource écht uit de jar komt,
                // niet uit build/resources op de test-classpath.
                ClassLoader.getPlatformClassLoader())) {

            MigrationSource fromJar = ClasspathMigrationSource.standard(jarLoader);
            List<Migration> loaded = fromJar.load();
            assertEquals(MigrationRegistry.RESOURCE_PATHS.size(), loaded.size());

            int applied = new MigrationRunner(database, fromJar).migrate();
            assertEquals(loaded.size(), applied);
            assertTrue(tableExists("claims"));
        }
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private List<Migration> loadOnly(int version) {
        for (String path : MigrationRegistry.RESOURCE_PATHS) {
            if (path.contains("V" + version + "__")) {
                return new ClasspathMigrationSource(
                        Thread.currentThread().getContextClassLoader(),
                        List.of(path)).load();
            }
        }
        throw new IllegalArgumentException("no such registered version V" + version);
    }

    private List<Migration> loadUpTo(int maxVersion) {
        List<String> subset = new ArrayList<>();
        for (String path : MigrationRegistry.RESOURCE_PATHS) {
            for (int v = 1; v <= maxVersion; v++) {
                if (path.contains("V" + v + "__")) subset.add(path);
            }
        }
        return new ClasspathMigrationSource(
                Thread.currentThread().getContextClassLoader(), subset).load();
    }

    private Path writeMigrationsJar(Path target) throws Exception {
        ClassLoader src = MigrationRunnerTest.class.getClassLoader();
        try (OutputStream fos = Files.newOutputStream(target);
             JarOutputStream jos = new JarOutputStream(fos)) {
            for (String path : MigrationRegistry.RESOURCE_PATHS) {
                byte[] bytes;
                try (var in = src.getResourceAsStream(path)) {
                    if (in == null) throw new IllegalStateException("missing test resource: " + path);
                    bytes = in.readAllBytes();
                }
                jos.putNextEntry(new JarEntry(path));
                jos.write(bytes);
                jos.closeEntry();
            }
        }
        // Sanity: force UTF-8 read-check
        assertTrue(Files.size(target) > 0);
        String probe = new String(Files.readAllBytes(target), StandardCharsets.ISO_8859_1);
        assertTrue(probe.contains("V1__"));
        return target;
    }

    private boolean tableExists(String name) {
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    private boolean schemaVersionContains(int version) {
        if (!tableExists("schema_version")) return false;
        return database.withConnection(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM schema_version WHERE version=?")) {
                ps.setInt(1, version);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }
}
