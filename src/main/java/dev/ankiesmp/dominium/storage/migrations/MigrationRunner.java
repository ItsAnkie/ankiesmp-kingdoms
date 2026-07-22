package dev.ankiesmp.dominium.storage.migrations;

import dev.ankiesmp.dominium.storage.db.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Past {@link Migration}s toe uit een {@link MigrationSource} op een
 * {@link Database}. Iedere migratie draait in één transactie:
 *
 * <ol>
 *   <li>schema-versie-tabel wordt (indien nodig) aangemaakt;</li>
 *   <li>per migratie: als versie al toegepast → skip;</li>
 *   <li>anders: SQL uitvoeren, dan {@code schema_version} INSERT,
 *       dan commit;</li>
 *   <li>faalt de SQL of de INSERT, dan rollt de transactie terug en
 *       wordt de rest niet uitgevoerd — startup faalt hard.</li>
 * </ol>
 *
 * <p>Logging (SLF4J) laat zien welke migraties gevonden, overgeslagen
 * en toegepast zijn zodat operators bij problemen niet in het duister
 * tasten.
 */
public final class MigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationRunner.class);

    private final Database database;
    private final MigrationSource source;

    /** Constructor voor productie: gebruikt de expliciete registry uit {@link MigrationRegistry}. */
    public MigrationRunner(Database database) {
        this(database, ClasspathMigrationSource.standard(MigrationRunner.class.getClassLoader()));
    }

    public MigrationRunner(Database database, MigrationSource source) {
        this.database = Objects.requireNonNull(database, "database");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * @return aantal daadwerkelijk toegepaste migraties (0 als alles al up-to-date is).
     * @throws IllegalStateException bij ontbrekende resources, duplicate versies of SQL-fouten.
     */
    public int migrate() {
        List<Migration> migrations = source.load();
        LOG.info("Dominium migrations discovered: {}", describe(migrations));

        ensureVersionTable();

        int applied = 0;
        for (Migration m : migrations) {
            boolean didApply = database.withTransaction(conn -> applyIfPending(conn, m));
            if (didApply) applied++;
        }
        if (applied == 0) {
            LOG.info("Dominium migrations: nothing to apply, schema up-to-date.");
        } else {
            LOG.info("Dominium migrations: {} migration(s) applied.", applied);
        }
        return applied;
    }

    private void ensureVersionTable() {
        database.execute(conn -> {
            try (Statement s = conn.createStatement()) {
                s.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version     INTEGER PRIMARY KEY,
                            applied_at  INTEGER NOT NULL,
                            description TEXT    NOT NULL
                        )
                        """);
            }
        });
    }

    private static boolean applyIfPending(Connection conn, Migration m) throws SQLException {
        if (isApplied(conn, m.version())) {
            LOG.info("  V{} '{}' -> SKIP (already applied)", m.version(), m.description());
            return false;
        }
        LOG.info("  V{} '{}' -> applying...", m.version(), m.description());
        for (String stmt : splitStatements(m.sql())) {
            if (stmt.isBlank()) continue;
            try (Statement s = conn.createStatement()) {
                s.execute(stmt);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO schema_version(version, applied_at, description) VALUES (?, ?, ?)")) {
            ps.setInt(1, m.version());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, m.description());
            ps.executeUpdate();
        }
        LOG.info("  V{} '{}' -> OK", m.version(), m.description());
        return true;
    }

    private static boolean isApplied(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM schema_version WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String describe(List<Migration> migrations) {
        if (migrations.isEmpty()) return "<none>";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < migrations.size(); i++) {
            if (i > 0) sb.append(", ");
            Migration m = migrations.get(i);
            sb.append('V').append(m.version()).append("(").append(m.description()).append(')');
        }
        return sb.toString();
    }

    /** SQL splitter die string-literals en comments respecteert. */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char[] chars = sql.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            char next = i + 1 < chars.length ? chars[i + 1] : '\0';

            if (inLineComment) {
                if (c == '\n') { inLineComment = false; cur.append(c); }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (!inSingle && !inDouble) {
                if (c == '-' && next == '-') { inLineComment = true; i++; continue; }
                if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            }
            if (c == '\'' && !inDouble) { inSingle = !inSingle; cur.append(c); continue; }
            if (c == '"' && !inSingle)  { inDouble = !inDouble; cur.append(c); continue; }

            if (c == ';' && !inSingle && !inDouble) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString());
        return out;
    }
}
