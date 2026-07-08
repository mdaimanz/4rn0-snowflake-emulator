package org.example.engine;

/**
 * Carries a SQL error back to the client in Snowflake's error-response shape.
 */
public class MockSqlException extends RuntimeException{
    private final String sqlState;
    private final String errorCode;

    public MockSqlException(String message, String sqlState, String errorCode) {
        super(message);
        this.sqlState = sqlState;
        this.errorCode = errorCode;
    }

    public String sqlState() { return sqlState == null ? "42000" : sqlState; }

    public String errorCode() { return errorCode == null ? "1003" : errorCode; }
}
