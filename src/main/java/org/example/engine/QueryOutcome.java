package org.example.engine;

import java.util.List;

/**
 * The result of executing one statement, ready to be serialized into a query response
 */
public record QueryOutcome (
        String queryId,
        List<SnowflakeColumn> columns,
        List<List<Object>> rows,
        long statementTypeId
){

}
