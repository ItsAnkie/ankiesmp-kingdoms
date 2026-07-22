package dev.ankiesmp.dominium.storage.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Dunne wrapper rond HikariCP voor Dominium. Biedt {@code withConnection}
 * en {@code withTransaction} helpers. Alle callers geven blocking SQL door;
 * de bootstrap zorgt ervoor dat deze op een dedicated executor draait,
 * nooit op de serverthread.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlAction<T> {
        T run(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface VoidSqlAction {
        void run(Connection conn) throws SQLException;
    }

    private final HikariDataSource dataSource;
    private final DatabaseDialect dialect;

    private Database(HikariDataSource dataSource, DatabaseDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    public static Database sqlite(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setMaximumPoolSize(1); // SQLite: één writer verhindert lock-noise
        cfg.setPoolName("dominium-sqlite");
        cfg.setAutoCommit(true);
        HikariDataSource ds = new HikariDataSource(cfg);
        // Zet pragma's; foreign keys en WAL blijven per connectie geldig zolang
        // de connectie leeft. Bij pool-size 1 dus feitelijk permanent.
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA synchronous=NORMAL");
        } catch (SQLException e) {
            ds.close();
            throw new IllegalStateException("Failed to initialise SQLite pragmas", e);
        }
        return new Database(ds, DatabaseDialect.SQLITE);
    }

    public DatabaseDialect dialect() {
        return dialect;
    }

    public <T> T withConnection(SqlAction<T> action) {
        try (Connection conn = dataSource.getConnection()) {
            return action.run(conn);
        } catch (SQLException e) {
            throw new DatabaseException("connection action failed", e);
        }
    }

    public <T> T withTransaction(SqlAction<T> action) {
        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = action.run(conn);
                conn.commit();
                return result;
            } catch (Throwable t) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    t.addSuppressed(rollbackEx);
                }
                if (t instanceof RuntimeException re) throw re;
                if (t instanceof Error err) throw err;
                throw new DatabaseException("transaction failed", t);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) { /* pool will discard if bad */ }
            }
        } catch (SQLException e) {
            throw new DatabaseException("transaction open failed", e);
        }
    }

    public void execute(VoidSqlAction action) {
        withConnection(c -> { action.run(c); return null; });
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
