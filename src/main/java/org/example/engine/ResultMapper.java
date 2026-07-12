package org.example.engine;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts a DuckDB {@link java.sql.ResultSet} into Snowflake's {@code rowtype} + {@code rowset} shape.
 */
public class ResultMapper {

    private ResultMapper(){}

    public record Mapped(List<SnowflakeColumn> columns, List<List<Object>> rows) {
    }

    public static List<SnowflakeColumn> columns(ResultSetMetaData md) throws SQLException {
        int count = md.getColumnCount();
        List<SnowflakeColumn> columns = new ArrayList<>(count);
        for(int i = 1; i<=count; i++) {
            columns.add(TypeMapper.map(md, i));
        }
        return columns;
    }

    public static Mapped map(ResultSet rs) throws SQLException {
        List<SnowflakeColumn> columns = columns(rs.getMetaData());
        List<List<Object>> rows = new ArrayList<>();
        int count = columns.size();
        while(rs.next()){
            List<Object> row = new ArrayList<>();
            for(int i = 1; i <= count; i++) {
                row.add(ValueEncoder.encode(rs, i, columns.get(i - 1).type()));
            }
            rows.add(row);
        }
        return new Mapped(columns, rows);
    }
}
