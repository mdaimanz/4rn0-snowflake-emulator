package org.example.engine;

/**
 * Snowflake logical column types as they appear on the wire in the {@code rowtype[].type} field.
 *
 * <p>The driver uses these lowercase names to choose how to decode each value in {@code rowset}.
 * The exact encoding for each type lives in {@link ValueEncoder}.
 */

public enum SnowflakeType {
    FIXED("fixed"),
    REAL("real"),
    TEXT("text"),
    BOOLEAN("boolean"),
    DATE("date"),
    TIME("time"),
    TIMESTAMP_NTZ("timestamp_ntz"),
    TIMESTAMP_LTZ("timestamp_ltz"),
    TIMESTAMP_TZ("timestamp_tz"),
    BINARY("binary"),
    VARIANT("variant"),
    OBJECT("object"),
    ARRAY("array");


    private final String wireName;

    SnowflakeType(String wireName) { this.wireName= wireName; }

    public String wireName() { return wireName; }
}
