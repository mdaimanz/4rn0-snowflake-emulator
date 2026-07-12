package org.example.engine;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.example.engine.StatementCategory.*;

@Component
public class QueryExecutor {

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
