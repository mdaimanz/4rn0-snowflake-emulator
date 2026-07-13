package org.example.session;

import org.example.config.MockSnowflakeProperties;
import org.example.engine.DuckDbEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Creates, looks up, renews and closes {@link Session} instances
 */
@Component
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private final DuckDbEngine engine;
    private final MockSnowflakeProperties properties;

    private final ConcurrentHashMap<String, Session> sessionsByToken = new ConcurrentHashMap<>();
    private final AtomicLong sessionIdSequence = new AtomicLong(1_000);
    private final SecureRandom random = new SecureRandom();

    private volatile Session defaultSession;

    public SessionManager(DuckDbEngine engine, MockSnowflakeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    public Session createSession(LoginInfo login) {
        MockSnowflakeProperties.Defaults defaults = properties.defaults();
        String database = firstNonBlank(login.database(), defaults.database());
        String schema = firstNonBlank(login.schema(), defaults.schema());
        String warehouse = firstNonBlank(login.warehouse(), defaults.warehouse());
        String role = firstNonBlank(login.role(), defaults.role());
        String account = firstNonBlank(login.account(), defaults.account());
        String user = firstNonBlank(login.user(), "MOCK_USER");

        Connection connection;

        try {
            connection = engine.openConnection(database, schema);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open DuckDB connection for session", e);
        }

        long id = sessionIdSequence.incrementAndGet();
        Session session = new Session(id, newToken(), newToken(), user, account,
                database, schema, warehouse, role, connection);
        sessionsByToken.put(session.token(), session);
        log.debug("Created session {} (db={}, schema={})", id, database, schema);
        return session;
    }

    public Session getByToken(String token) { return token == null ? null : sessionsByToken.get(token); }

    /**
     * Returns the session bound to {@code token}, or a shared default session when the token is
     * unknown. Being lenient keeps the mock usable even when token plumbing differs across driver
     * versions.
     */
    public Session resolveOrDefault(String token) {
        Session session = getByToken(token);
        return session != null ? session : defaultSession();
    }

    public Session defaultSession() {
        Session existing = defaultSession;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (defaultSession == null) {
                MockSnowflakeProperties.Defaults d = properties.defaults();
                defaultSession = createSession(
                        new LoginInfo("MOCK_USER",
                                d.account(),
                                d.database(),
                                d.schema(),
                                d.warehouse(),
                                d.role()));
            }
        }
        return defaultSession;
    }

    public Session renew(String oldToken) {
        Session session = getByToken(oldToken);
        if (session == null) {
            return null;
        }
        sessionsByToken.remove(oldToken);
        session.setToken(newToken());
        session.setMasterToken(newToken());
        sessionsByToken.put(session.token(), session);
        return session;
    }

    public void close(String token) {
        Session session = sessionsByToken.remove(token);
        if(session == null) {
            return;
        }
        try {
            session.connection().close();
        } catch (SQLException e) {
            log.debug("Error closing session connection", e);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "mock: " + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String firstNonBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
