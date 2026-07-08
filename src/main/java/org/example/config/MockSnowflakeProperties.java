package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the mock server, bound from the {@code mock.snowflake.*} namespace.
 *
 * <p>All values have sensible defaults so the server runs with zero configuration, which is what
 * the Docker image relies on. Override any value through environment variables, e.g.
 * {@code MOCK_SNOWFLAKE_DUCKDB_URL=jdbc:/data/test.duckdb}.
 */
@ConfigurationProperties("mock.snowflake")
public record MockSnowflakeProperties(
        DuckDb duckDb,
        Defaults defaults,
        @DefaultValue("8.40.0") String serverVersion,
        @DefaultValue("3600") long sessionValiditySeconds,
        @DefaultValue("14400") long masterValiditySeconds
) {

    public MockSnowflakeProperties {
        if (duckDb == null) {
            duckDb = new DuckDb("jdbc:duckdb:");
        }
        if (defaults == null) {
            defaults = new Defaults(null, null, null, null, null, null);
        }
    }

    public record DuckDb(@DefaultValue("jdbc:duckdb:") String url) {
        public DuckDb {
            if (url == null || url.isBlank()) {
                url = "jdbc:duckdb:";
            }
        }
    }

    public record Defaults(
            @DefaultValue("TESTDB") String database,
            @DefaultValue("PUBLIC") String schema,
            @DefaultValue("COMPUTE_WH") String warehouse,
            @DefaultValue("ACCOUNT_ADMIN") String role,
            @DefaultValue("MOCK") String account,
            @DefaultValue("AWS_US_WEST_2") String region) {
        public Defaults {
            database = orDefault(database, "TESTDB");
            schema = orDefault(schema, "PUBLIC");
            warehouse = orDefault(warehouse, "COMPUTE_WH");
            role = orDefault(role, "ACCOUNT_ADMIN");
            account = orDefault(account, "MOCK");
            region = orDefault(region, "AWS_US_WEST_2");
        }

        private static String orDefault(String value, String fallback) {
            return (value == null || value.isBlank()) ? fallback : value;
        }
    }

}
