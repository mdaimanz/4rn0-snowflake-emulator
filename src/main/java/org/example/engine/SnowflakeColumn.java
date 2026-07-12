package org.example.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single result column describe the way Snowflake describes it in {@code rowType}
 */
public record SnowflakeColumn (
        String name,
        SnowflakeType type,
        int precision,
        int scale,
        long length,
        long byteLength,
        boolean nullable
){
    /**
     * Serializes to the map structure expected inside the {@code rowtype} array.
     */
    public Map<String, Object> toRowType() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("database", "");
        m.put("schema", "");
        m.put("table", "");
        m.put("type", type.wireName());
        m.put("scale", typeHasScale() ? precision : null);
        m.put("precision", typeHasPrecision() ? precision : null);
        m.put("length", typeHasLength() ? length : null);
        m.put("byteLength", type == SnowflakeType.BINARY ? byteLength : null);
        m.put("nullable", nullable);
        m.put("collation", null);
        return m;
    }

    private boolean typeHasScale() {
        return switch (type) {
            case FIXED, TIME, TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ -> true;
            default -> false;
        };
    }

    private boolean typeHasPrecision() { return type == SnowflakeType.FIXED; }

    private boolean typeHasLength() { return type == SnowflakeType.TEXT || type == SnowflakeType.BINARY; }
}
