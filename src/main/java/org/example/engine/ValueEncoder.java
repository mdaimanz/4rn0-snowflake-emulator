package org.example.engine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public final class ValueEncoder {
    private static final DateTimeFormatter FLEX_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[ ['T']HH:mm[:ss]]")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "Z").optionalEnd()
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            .toFormatter();

    private ValueEncoder(){}

    public static Object encode(ResultSet rs, int col, SnowflakeType type) throws SQLException {
        return switch (type) {
            case BOOLEAN -> {
                boolean b = rs.getBoolean(col);
                yield rs.wasNull() ? null : (b ? "1" : "0");
            }
            case REAL -> {
                double d = rs.getDouble(col);
                yield rs.wasNull() ? null : Double.toString(d);
            }
            case FIXED, TEXT, VARIANT, OBJECT, ARRAY -> rs.getString(col);
            case BINARY -> {
                byte[] bytes = rs.getBytes(col);
                yield bytes == null ? null : toHex(bytes);
            }
            case DATE -> {
                LocalDate d = javaTime(rs, col, LocalDate.class);
                yield d == null ? null : Long.toString(d.toEpochDay());
            }
            case TIME -> {
                LocalTime t = javaTime(rs, col, LocalTime.class);
                yield t == null ? null : fractionalSeconds(t.toNanoOfDay());
            }
            case TIMESTAMP_NTZ -> {
                LocalDateTime ts = javaTime(rs, col, LocalDateTime.class);
                yield ts == null ? null : epochFraction(ts.toEpochSecond(ZoneOffset.UTC), ts.getNano());
            }
            case TIMESTAMP_LTZ -> {
                OffsetDateTime odt = javaTime(rs, col, OffsetDateTime.class);
                if (odt == null) {
                    yield null;
                }
                Instant i = odt.toInstant();
                yield epochFraction(i.getEpochSecond(), i.getNano());
            }
            case TIMESTAMP_TZ -> {
                OffsetDateTime odt = javaTime(rs, col, OffsetDateTime.class);
                if (odt == null){
                    yield null;
                }
                Instant i = odt.toInstant();
                int offsetMinutes = odt.getOffset().getTotalSeconds() / 60;
                yield epochFraction(i.getEpochSecond(), i.getNano()) + " " + (offsetMinutes + 1440);
            }
        };
    }

    private static String fractionalSeconds(long nanoOfDay) {
        long secs = nanoOfDay / 1_000_000_000L;
        long frac = nanoOfDay % 1_000_000_000L;
        return secs + "." + pad9(frac);
    }

    private static String epochFraction(long epochSeconds, int nanos) { return epochSeconds + "." + pad9(nanos); }

    private static String pad9(long value){
        String s = Long.toString(value);
        return "0".repeat(9 - s.length()) + s;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b: bytes){
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString().toUpperCase();
    }

    @SuppressWarnings("uncheck")
    private static <T>T javaTime(ResultSet rs, int col, Class<T> type) throws SQLException {
        try {
            T value = rs.getObject(col, type);
            if(value != null || rs.wasNull()) {
                return value;
            }
        } catch (SQLException | RuntimeException ignored) {
            // Fall back to string parsing below
        }
        String s = rs.getString(col);
        if (s == null){
            return null;
        }
        if (type == LocalDate.class) {
            return (T) LocalDate.parse(s);
        }
        if (type == LocalTime.class) {
            return (T) LocalTime.parse(s);
        }
        if (type == LocalDateTime.class) {
            return (T) LocalDateTime.parse(s.replace(' ', 'T'));
        }
        if (type == OffsetDateTime.class) {
            return (T) OffsetDateTime.parse(s, FLEX_DATE_TIME);
        }
        return null;
    }
}
