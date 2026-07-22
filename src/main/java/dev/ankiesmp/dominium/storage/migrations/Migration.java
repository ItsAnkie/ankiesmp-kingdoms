package dev.ankiesmp.dominium.storage.migrations;

import java.util.Objects;

/**
 * Eén geordende schema-migratie. Geregistreerd als geordende SQL-scripts
 * in {@code resources/db/migrations/V{n}__{name}.sql}.
 */
public final class Migration {

    private final int version;
    private final String description;
    private final String sql;

    public Migration(int version, String description, String sql) {
        if (version <= 0) throw new IllegalArgumentException("version must be > 0");
        this.description = Objects.requireNonNull(description, "description");
        this.sql = Objects.requireNonNull(sql, "sql");
        this.version = version;
    }

    public int version() { return version; }
    public String description() { return description; }
    public String sql() { return sql; }
}
