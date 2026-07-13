package org.example.session;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

public final class Session {

    private final long sessionId;
    private volatile String token;
    private volatile String masterToken;
    private final String user;
    private final String account;
    private volatile String database;
    private volatile String schema;
    private volatile String warehouse;
    private volatile String role;
    private final Connection connection;
    private final AtomicLong sequenceCounter = new AtomicLong();

    public Session(long sessionId, String token, String masterToken, String user, String account, String database,
                   String schema, String warehouse, String role, Connection connection) {
        this.sessionId = sessionId;
        this.token = token;
        this.masterToken = masterToken;
        this.user = user;
        this.account = account;
        this.database = database;
        this.schema = schema;
        this.warehouse = warehouse;
        this.role = role;
        this.connection = connection;
    }

    public long sessionId() {
        return sessionId;
    }

    public String token() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String masterToken() {
        return masterToken;
    }

    public void setMasterToken(String masterToken) {
        this.masterToken = masterToken;
    }

    public String user() {
        return user;
    }

    public String account() {
        return account;
    }

    public String database() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String schema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String warehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }

    public String role() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Connection connection() {
        return connection;
    }

    public AtomicLong getSequenceCounter() {
        return sequenceCounter;
    }

    public long nextSequenceId() { return sequenceCounter.incrementAndGet(); }
}
