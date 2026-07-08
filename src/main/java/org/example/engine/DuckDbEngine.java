package org.example.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.duckdb.DuckDBConnection;
import org.example.config.MockSnowflakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the embedded DuckDB database.
 *
 * <p>A single root {@link DuckDBConnection} is kept open for the lifetime of the server. Every
 * session gets its own connection via {@link DuckDBConnection#duplicate()} so it can be hold an
 * independent current database/schema while sharing the same data (this is the only way to share an
 * in-memory DuckDB across connection).
 *
 * <p>Snowflake databases and schemas are modelled as DuckDB attached in-memory catalogs and
 * schemas, which makes one-, two- and three-part identifiers all resolve naturally.
 */
@Component
public class DuckDbEngine {

    private static final Logger log = LoggerFactory.getLogger(DuckDbEngine.class);

    private final MockSnowflakeProperties properties;
    private final Set<String> initializedSchemas = ConcurrentHashMap.newKeySet();

    private DuckDBConnection root;
    public DuckDbEngine(MockSnowflakeProperties properties) { this.properties = properties; }

    @PostConstruct
    void init() throws SQLException, ClassNotFoundException {
        Class.forName("org.duckdb.DuckDBDriver");
        String url = properties.duckDb().url();
        root = (DuckDBConnection) DriverManager.getConnection(url);
        ensureContext(root, properties.defaults().database(), properties.defaults().schema());
        log.info("DuckDB engine initialized (url={})", url);
    }

    @PreDestroy
    void shutdown() {
        if (root != null) {
            try {
                root.close();
            } catch (SQLException e) {
                log.debug("Error closing DuckDB root connection", e);
            }
        }
    }

    /**
     * Opens a fresh session connection positioned at {@code database.schema}.
     */
    public Connection openConnection(String database, String schema) throws SQLException {
        Connection connection = root.duplicate();
        ensureContext(connection, database, schema);
        return connection;
    }

    /**
     * Ensures the given database (attached catalog) and schema exist, installs compability macros
     * the first time a schema is seen, and switches the connection to it.
     */
    public void ensureContext(Connection connection, String database, String schema) throws SQLException {
        String db = sanitize(database, properties.defaults().database());
        String sc = sanitize(schema, properties.defaults().schema());
        try (Statement st = connection.createStatement()) {
            st.execute("ATTACH IF NOT EXISTS ':memory' AS " + quote(db));
            st.execute("CREATE SCHEMA IF NOT EXISTS " + quote(db) + "." + quote(sc));
            st.execute("USE " + quote(db) + "." + quote(sc));
        }
        String key = db.toUpperCase(Locale.ROOT) + "." + sc.toUpperCase(Locale.ROOT);
        if (initializedSchemas.add(key)) {
            SnowflakeCompat.applyTo(connection, db, sc, properties);
        }
    }

    private static String sanitize(String value, String fallback) {
        String v = (value == null || value.isBlank()) ? fallback : value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.toUpperCase(Locale.ROOT);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

}
