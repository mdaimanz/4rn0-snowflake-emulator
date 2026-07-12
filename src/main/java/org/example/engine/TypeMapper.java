package org.example.engine;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Maps DuckDB column metadata to the {@link SnowflakeColumn} description Snowflake clients expect
 */
public final class TypeMapper {

    private static final long DEFAULT_TEXT_LENGTH = 16_777_216L;
    private static final long DEFAULT_BINARY_LENGTH = 8_388_608L;
    private static final int MAX_FIXED_PRECISION = 38;
    private static final int TEMPORAL_SCALE = 9;

    private TypeMapper(){}

    public static SnowflakeColumn map(ResultSetMetaData md, int col) throws SQLException {
        String name = md.getColumnLabel(col).toUpperCase(Locale.ROOT);
        String rawType = md.getColumnTypeName(col);
        int precision = md.getPrecision(col);
        int scale = md.getScale(col);
        boolean nullable = md.isNullable(col) != ResultSetMetaData.columnNoNulls;

        SnowflakeType type = toSnowflakeType(rawType);
        return switch(type) {
            case FIXED -> new SnowflakeColumn(name, type,
                    clampPrecision(precision), Math.max(scale,0), 0, 0, nullable);
            case TEXT, VARIANT, OBJECT, ARRAY -> new SnowflakeColumn(name, type,
                    0, 0, precision>0 ? precision: DEFAULT_TEXT_LENGTH, 0, nullable);
            case BINARY -> {
                long len = precision > 0 ? precision : DEFAULT_BINARY_LENGTH;
                yield new SnowflakeColumn(name, type, 0, 0, len, len, nullable);
            }
            case TIME, TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ -> new SnowflakeColumn(name, type,
                    0, TEMPORAL_SCALE, 0, 0, nullable);
            default -> new SnowflakeColumn(name, type, 0, 0, 0, 0, nullable);
        };
    }

    static SnowflakeType toSnowflakeType(String rawType) {
        if (rawType == null) {
            return SnowflakeType.TEXT;
        }
        String upper = rawType.toUpperCase(Locale.ROOT).trim();
        if(upper.endsWith("[]") || upper.startsWith("LIST")) {
            return SnowflakeType.ARRAY;
        }
        if(upper.startsWith("STRUCT") || upper.startsWith("MAP") || upper.startsWith("UNION")) {
            return SnowflakeType.OBJECT;
        }
        String base = baseType(upper);
        return switch(base) {
            case "BOOLEAN", "BOOL", "LOGICAL", "BIT" -> SnowflakeType.BOOLEAN;
            case "TINYINT", "INT1", "SMALLINT", "INT2", "SHORT", "INTEGER", "INT", "INT4",
                    "SIGNED", "BIGINT", "INT8", "LONG", "HUGEINT", "INT128", "UTINYINT",
                    "USMALLINT", "UINTEGER", "UBIGINT", "UHUGEINT", "DECIMAL", "NUMERIC" -> SnowflakeType.FIXED;
            case "FLOAT", "FLOAT4", "REAL", "DOUBLE", "FLOAT8" -> SnowflakeType.REAL;
            case "DATE" -> SnowflakeType.DATE;
            case "TIME", "TIMETZ" -> SnowflakeType.TIME;
            case "TIMESTAMP", "DATETIME", "TIMESTAMP_S", "TIMESTAMP_MS", "TIMESTAMP_NS" -> SnowflakeType.TIMESTAMP_NTZ;
            case "TIMESTAMPZ", "TIMESTAMP_TZ" -> SnowflakeType.TIMESTAMP_LTZ;
            case "BLOB", "BYTEA", "BINARY", "VARBINARY" -> SnowflakeType.BINARY;
            case "JSON" -> SnowflakeType.VARIANT;
            case "VARCHAR", "CHAR", "BPCHAR", "TEXT", "STRING", "UUID", "INTERVAL", "ENUM" -> SnowflakeType.TEXT;
            default -> SnowflakeType.TEXT;
        };
    }

    private static String baseType(String upper) {
        int paren = upper.indexOf('(');
        return (paren >= 0 ? upper.substring(0, paren) : upper).trim();
    }

    private static int clampPrecision(int precision) {
        if (precision <= 0){
            return MAX_FIXED_PRECISION;
        }
        return Math.min(precision, MAX_FIXED_PRECISION);
    }
}
