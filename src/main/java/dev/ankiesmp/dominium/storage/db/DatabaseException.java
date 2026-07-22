package dev.ankiesmp.dominium.storage.db;

/**
 * Unchecked wrapper voor onverwachte SQL-fouten binnen Dominium's
 * storage-laag. Callers hoeven de checked {@link java.sql.SQLException}
 * niet overal door te lussen.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
