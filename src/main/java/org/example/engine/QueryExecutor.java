package org.example.engine;

import org.example.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.example.engine.StatementCategory.*;

@Component
public class QueryExecutor {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutor.class);

    /**
     * CREATE/DROP/ALTER targeting Snowflake-only account objects -> treated as no-ops
     */
    private static final Pattern IGNORED_DDL = Pattern.compile(
            "^(?:CREATE(?:\\s+OR\\s+REPLACE)?|DROP|ALTER)"
            + "(?:\\s+(?:TEMP|TEMPORARY|TRANSIENT|SECURE|GLOBAL|LOCAL|VOLATILE))*"
            + "\\s+(?:IF\\s(?:NOT\\s+)?EXISTS\\s+)?"
            + "(?:WAREHOUSE|RESOURCE\\s+MONITOR|ROLE|USER|SHARE|NETWORK\\s+POLICY|STAGE|PIPE"
            + "|STREAM|TASK|FILE\\s+FORMAT|MASKING\\s+POLICY|ROW\\s+ACCESS\\s+POLICY|TAG"
            + "|SECURITY\\s+INTEGRATION|STORAGE|\\s+INTEGRATION|API\\s+INTEGRATION"
            + "|NOTIFICATION\\s+INTEGRATION|EXTERNAL\\s+FUNCTION|PROCEDURE|FUNCTION)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * CREATE/DROP/ALTER DATABASE|SCHEMA, capturing action, object type and name.
     */
    private static final Pattern DB_SCHEMA_DDL = Pattern.compile(
            "^(?:CREATE(?:\\s+OR\\s+REPLACE)?|DROP|ALTER)"
            + "(?:\\s+(?:TEMP|TEMPORARY|TRANSIENT|SECURE|GLOBAL|LOCAL|VOLATILE))*"
            + "\\s+(?:IF\\s+(?:NOT\\s+)?EXISTS\\s+)?"
            + "(?:WAREHOUSE|RESOURCE\\s+MONITOR|ROLE|USER|SHARE|NETWORK\\s+POLICY|STAGE|PIPE"
            + "|STREAM|TASK|FILE\\s+FORMAT|MASKING\\s+POLICY|ROW\\s+ACCESS\\s+POLICY|TAG"
            + "|SECURITY\\s+INTEGRATION|STORAGE\\s+INTEGRATION|API\\s+INTEGRATION"
            + "|NOTIFICATION\\s+INTEGRATION|EXTERNAL\\s+FUNCTION|PROCEDURE|FUNCTION)\\b"
            ,Pattern.CASE_INSENSITIVE
    );

    private final DuckDbEngine engine;

    public QueryExecutor(DuckDbEngine engine) { this.engine = engine; }

    public QueryOutcome execute(Session session, String sqlText, Map<String,Object> bindings, boolean describeOnly) {
        String queryId = UUID.randomUUID().toString();
        String sql = stripTrailingSemicolon(sqlText == null ? "" : sqlText.strip());
        StatementCategory category = StatementClassifier.classify(sql);

        try {
            if (category == SCL ) {
                return handleSessionControl(session, sql, queryId);
            }
            if (category == TCL) {
                return status(queryId, "Statement executed successfully.");
            }
            if (category == DDL) {
                QueryOutcome special = handleDdl(session, sql, queryId);
                if (special != null) {
                    return special;
                }
            }

            String translated = SqlTranslator.translate(sql, category);
            if (describeOnly) {
                return describe(session, translated, queryId);
            }
            return run(session, translated, category, bindings, queryId);
        } catch (SQLException e) {
            log.debug("SQL error executing [{}]: {}", sql, e.getMessage());
            throw new MockSqlException(e.getMessage(), e.getSQLState(), null);
        }
    }

    // ----- Execution ----------

    private QueryOutcome run(Session session, String sql, StatementCategory category,
                             Map<String, Object> bindings, String queryId) throws SQLException {
        Connection conn = session.connection();
        if (bindings == null || bindings.isEmpty()) {
            try (Statement st = conn.createStatement()) {
                boolean hasResultSet = st.execute(sql);
                return collect(st, hasResultSet, category, queryId);
            }
        }

        int batchSize = arrayBindLength(bindings);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (batchSize > 0) {
                for (int row = 0; row < batchSize; row++) {
                    applyBindings(ps, bindings, row);
                    ps.addBatch();
                }
                long total = 0;
                for (int count : ps.executeBatch()) {
                    total += Math.max(count, 0);
                }
                return category.isDml() ? dmlCount(queryId, category, total) :
                        status(queryId, "Statement executed successfully.");
            }
            applyBindings(ps, bindings, -1);
            boolean hasResultSet = ps.execute();
            return collect(ps, hasResultSet, category, queryId);
        }
    }

    private QueryOutcome collect(Statement st, boolean hasResultSet, StatementCategory category,
                                 String queryId) throws SQLException {
        if (hasResultSet) {
            try (ResultSet rs = st.getResultSet()) {
                ResultMapper.Mapped mapped = ResultMapper.map(rs);
                long typeId = category.isDml() ? category.typeId() : SELECT.typeId();
                return new QueryOutcome(queryId, mapped.columns(), mapped.rows(), typeId);
            }
        }
        int updateCount = st.getUpdateCount();
        if (category.isDml()) {
            return dmlCount(queryId, category, Math.max(updateCount, 0));
        }
        return status(queryId, "Statement executed successfully.");
    }

    private QueryOutcome describe(Session session, String sql, String queryId) {
        try (PreparedStatement ps = session.connection().prepareStatement(sql)) {
            ResultSetMetaData md = ps.getMetaData();
            List<SnowflakeColumn> columns = (md == null) ? List.of() : ResultMapper.columns(md);
            return new QueryOutcome(queryId, columns, List.of(), SELECT.typeId());
        } catch (SQLException e) {
            return new QueryOutcome(queryId, List.of(), List.of(), SELECT.typeId());
        }
    }

    // --- Special command handling ------------
    private QueryOutcome handleSessionControl(Session session, String sql, String queryId) throws SQLException {
        String upper = sql.toUpperCase(Locale.ROOT);
        if(!upper.startsWith("USE")) {
            // SET / UNSET / ALTER SESSION - accepted and ignored
            return status(queryId, "Statement execute successfully");
        }

        String rest = sql.substring(3).strip();
        String keyword = firstToken(rest).toUpperCase();
        String argument = switch (keyword) {
            case "WAREHOUSE", "ROLE", "DATABASE", "SCHEMA" -> rest.substring(keyword.length()).strip();
            default -> rest;
        };
        String name = cleanIdentifier(firstToken(argument));

        switch (keyword) {
            case "WAREHOUSE" -> session.setWarehouse(name.toUpperCase(Locale.ROOT));
            case "ROLE" -> session.setRole(name.toUpperCase(Locale.ROOT));
            case "DATABASE" -> {
                engine.ensureContext(session.connection(), name, session.schema());
                session.setDatabase(name.toUpperCase(Locale.ROOT));
            }
            default -> applyUseSchema(session, name);
        }
        return status(queryId, "Statement executed successfully.");
    }

    private void applyUseSchema(Session session, String name) throws SQLException {
        String[] parts = name.split("\\.");
        String db = parts.length > 1 ? cleanIdentifier(parts[0]) : session.database();
        String schema = cleanIdentifier(parts[parts.length -1]);
        engine.ensureContext(session.connection(), db, schema);
        session.setDatabase(db.toUpperCase(Locale.ROOT));
        session.setSchema(schema.toUpperCase(Locale.ROOT));
    }

    private QueryOutcome handleDdl(Session session, String sql, String queryId) throws SQLException {
        String first = firstToken(sql).toUpperCase(Locale.ROOT);
        if (first.equals("GRANT") || first.equals("REVOKE")) {
            return status(queryId, "Statement executed successfully");
        }
        if(IGNORED_DDL.matcher(sql).find()) {
             return status(queryId, "Statement executed successfully");
        }
        Matcher m = DB_SCHEMA_DDL.matcher(sql);
        if (m.find()) {
            String action = m.group(1).toUpperCase(Locale.ROOT);
            String objectType = m.group(2).toUpperCase(Locale.ROOT);
            String name = cleanIdentifier(m.group(3));
            if (action.startsWith("CREATE")) {
                if(objectType.equals("DATABASE")) {
                    engine.ensureContext(session.connection(), name, session.schema());
                    session.setDatabase(name.toUpperCase(Locale.ROOT));
                } else {
                    applyUseSchema(session, name);
                }
            }
            // DROP / ALTER DATABASE / SCHEMA are accepted but not enacted
            return status(queryId, "Statement executed successfully");
        }
        return null; // Hands off to DuckDB (CREATE TABLE / VIEW / SEQUENCE / ...)
    }

    // ---- Bindings ----------------
    private void applyBindings(PreparedStatement ps, Map<String, Object> bindings, int row) throws SQLException {
        List<Integer> indexes = new ArrayList<>();
        for (String key : bindings.keySet()) {
            if (key.matches("\\d+")) {
                indexes.add(Integer.parseInt(key));
            }
        }
        indexes.sort(Integer::compareTo);
        for (int index : indexes) {
            Object raw = bindings.get(String.valueOf(index));
            if (!(raw instanceof Map<?, ?> bind)) {
                ps.setObject(index, raw);
                continue;
            }
            Object typeObj = bind.get("type");
            String type = (typeObj == null ? "TEXT" : typeObj.toString()).toUpperCase(Locale.ROOT);
            Object valueObj = bind.get("value");
            if (valueObj instanceof List<?> list) {
                valueObj = (row >= 0 && row < list.size()) ? list.get(row) : null;
            }
            if (valueObj == null) {
                ps.setObject(index, null);
                continue;
            }
            setTypedParameter(ps, index, type, valueObj.toString());
        }
    }

    private void setTypedParameter(PreparedStatement ps, int index, String type, String value) throws SQLException {
        try {
            switch(type) {
                case "FIXED", "INTEGER" -> {
                    if (value.contains(".") || value.contains("E") || value.contains("e")){
                        ps.setBigDecimal(index, new BigDecimal(value));
                    }
                }
                case "REAL", "DOUBLE" -> ps.setDouble(index, Double.parseDouble(value));
                case "BOOLEAN" -> ps.setBoolean(index, value.equals("1") || Boolean.parseBoolean(value));
                case "DATE" -> ps.setObject(index, LocalDate.ofEpochDay(Long.parseLong(value)));
                case "TIMESTAMP_NTZ", "TIMESTAMP_LTZ", "TIMESTAMP_NZ", "TIMESTAMP" ->
                    ps.setObject(index, parseEpochInstant(value));
                case "BINARY" -> ps.setBytes(index, hexToBytes(value));
                default -> ps.setString(index, value);
            }
        } catch (RuntimeException e) {
            ps.setString(index, value);
        }
    }

    private static Instant parseEpochInstant(String value) {
        String numeric = value.split("\\s+")[0];
        int dot = numeric.indexOf('.');
        long seconds = Long.parseLong(dot >= 0 ? numeric.substring(0, dot) : numeric);
        long nanos = 0;
        if (dot >= 0){
            String frac = (numeric.substring(dot + 1) + "000000000").substring(0, 9);
            nanos = Long.parseLong(frac);
        }
        return Instant.ofEpochSecond(seconds, nanos);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++){
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static int arrayBindLength(Map<String, Object> bindings) {
        int length = 0;
        for (Object raw : bindings.values()) {
            if (raw instanceof Map<?, ?> bind && bind.get("value") instanceof List<?> list) {
                length = Math.max(length, list.size());
            }
        }
        return length;
    }


    // ---- Outcome builders -------------------

    private QueryOutcome dmlCount(String queryId, StatementCategory category, long count) {
        String columnName = switch(category) {
            case INSERT, MULTI_INSERT -> "number of rows inserted";
            case UPDATE -> "number of rows updated";
            case DELETE -> "number of rows deleted";
            case MERGE -> "number of rows inserted or updated";
            default -> "rows affected";
        };
        SnowflakeColumn column = new SnowflakeColumn(columnName, SnowflakeType.FIXED, 19, 0, 0, 0, false);
        List<List<Object>> rows = List.of(List.of(Long.toString(count)));
        return new QueryOutcome(queryId, List.of(column), rows, category.typeId());
    }

    private QueryOutcome status(String queryId, String message) {
        SnowflakeColumn column = new SnowflakeColumn("status", SnowflakeType.TEXT,
                0, 0, 16_777_216L, 0 , false);
        List<List<Object>> rows = List.of(List.of(message));
        return new QueryOutcome(queryId, List.of(column), rows, StatementCategory.DDL.typeId());
    }

    // ---- Helpers --------
    private static String stripTrailingSemicolon(String sql) {
        String s = sql.strip();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        return s;
    }

    private static String firstToken(String s) {
        String t = s.strip();
        int i = 0;
        while (i < t.length() && !Character.isWhitespace(t.charAt(i))){
            i++;
        }
        return t.substring(0, i);
    }

    private static String cleanIdentifier(String token) {
        String t = token.strip();
        if (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1);
        }
        t = t.replace("\"", "");
        return t;
    }
}
