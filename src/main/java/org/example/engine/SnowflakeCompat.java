package org.example.engine;


import org.example.config.MockSnowflakeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Installs Snowflake-compatible SQL functions into a DuckDB schema as macros.
 *
 * <p>Each macro is created with {@code CREATE OR REPLACE MACRO} and executed independently;
 * a failure (for example because DuckDB already ships an identically named built-in) is logged at
 * debug level and ignored, so one bad entry never breaks startup.
 *
 * <p><b>Maintainers:</b> add new Snowflake functions to {@link #scalarMacros(MockSnowflakeProperties)}.
 * Prefer expressing them in terms of DuckDB built-ins. Session-aware functons
 * ({@code CURRENT_WAREHOUSE} etc.) return configured defaults because macros are static.
 */
public final class SnowflakeCompat {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeCompat.class);

    private SnowflakeCompat() {
    }

    /**
     * Creates all compability macros inside {@code "db"."schema"}.
     */
    public static void applyTo(Connection conn,
                               String db,
                               String schema,
                               MockSnowflakeProperties properties) {
        String schemaPrefix = quote(db) + "." + quote(schema);
        try (Statement st = conn.createStatement()) {
            for (Macro macro: scalarMacros(properties)) {
                String sql = "CREATE OR REPLACE MACRO " + schemaPrefix + "." + macro.signature()
                        + " AS " + macro.body();
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    log.debug("Skipping macro {} ({})", macro.signature(), e.getMessage());
                }
            }
        } catch (SQLException e) {
            log.warn("Unable to install Snowflake compability macros: {}", e.getMessage());
        }
    }

    private static List<Macro> scalarMacros(MockSnowflakeProperties properties) {
        MockSnowflakeProperties.Defaults d = properties.defaults();
        return new ArrayList<>(List.of(
                // Null handling
                new Macro("nvl(a, b)", "coalesce(a, b)"),
                new Macro("ifnull(a, b)", "coalesce(a, b)"),
                new Macro("nvl2(a, b, c)", "CASE WHEN a IS NOT NULL THEN b ELSE c END"),
                new Macro("iff(c, a, b)", "CASE WHEN c THEN a ELSE b END"),
                new Macro("zeroifnull(a)", "coalesce(a, 0)"),
                new Macro("nullifzero(a)", "nullif(a, 0)"),
                new Macro("equal_null(a, b)", "a IS NOT DISTINCT FROM b"),
                new Macro("booland(a, b)", "(a AND b)"),
                new Macro("boolor(a, b)", "(a OR b)"),
                new Macro("boolnot(a)", "NOT(a)"),
                new Macro("div0(a, b)", "CASE WHEN b = 0 THEN 0 ELSE a / b END"),
                new Macro("div0null(a, b)", "CASE WHEN b = 0 OR b IS NULL THEN 0 ELSE a / b END"),
                new Macro("square(a)", "(a * a)"),
                // Conversion
                new Macro("to_varchar(x)","CAST(x AS VARCHAR)"),
                new Macro("to_char(x)", "CAST(x AS VARCHAR)"),
                new Macro("to_number(x)", "CAST(x AS DECIMAL(38,0))"),
                new Macro("to_numeric(x)", "CAST(x AS DECIMAL(38,0))"),
                new Macro("to_decimal(x)", "CAST(x AS DECIMAL(38,0))"),
                new Macro("to_double(x)", "CAST(x AS DOUBLE)"),
                new Macro("to_boolean(x)", "CAST(x AS DOUBLE)"),
                new Macro("to_date(x)", "CAST(x AS DATE)"),
                new Macro("to_timestamp_ntz(x)", "CAST(x AS TIMESTAMP)"),
                new Macro("to_timestamp_ltz(x)", "CAST(x AS TIMESTAMPZ)"),
                new Macro("try_to_number(x)", "TRY_CAST(x AS DECIMAL(38,0))"),
                new Macro("try_to_double(x)", "TRY_CAST(x AS DOUBLE)"),
                new Macro("try_to_boolean(x)", "TRY_CAST(x AS BOOLEAN)"),
                new Macro("try_to_date(x)", "TRY_CAST(x AS DATE)"),
                // Strings
                new Macro("charindex(needle, haystack)", "position(needle IN haystack)"),
                new Macro("startswith(s, p)", "starts_with(s, p)"),
                new Macro("endswith(s, p)", "ends_with(s, p)"),
                new Macro("rtrimmed_length(s)", "length(rtrim(s))"),
                new Macro("collate(s, c)", "s"),
                // Semi-structured
                new Macro("parse_json(x)", "CAST(x AS JSON)"),
                new Macro("try_parse_json(x)", "TRY_CAST(x AS JSON)"),
                new Macro("to_json(x)", "CAST(x AS JSON)"),
                new Macro("to_variant(x)", "CAST(x AS JSON)"),
                new Macro("array_size(x)", "json_array_length(x)"),
                // Date / time
                new Macro("sysdate()", "now()"),
                new Macro("systimestamp()", "now()"),
                new Macro("getdate()", "now()"),

                // Context (static defaults - see class doc)
                new Macro("current_warehouse()", literal(d.warehouse())),
                new Macro("current_role()", literal(d.role())),
                new Macro("current_account()", literal(d.account())),
                new Macro("current_account_name()", literal(d.account())),
                new Macro("current_region()", literal(d.region())),
                new Macro("current_version()", literal(properties.serverVersion())),
                new Macro("current_client()", literal("MockSnowflake")),
                new Macro("current_organization_name()", literal("MOCK_ORG"))
        ));
    }


    private static String literal(String value) { return "'" + (value == null ? "" : value.replace("'", "''")) + "'"; }

    private static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }

    private record Macro(String signature, String body) {}

}
