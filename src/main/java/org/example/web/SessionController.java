package org.example.web;

import org.example.config.MockSnowflakeProperties;
import org.example.protocol.Responses;
import org.example.session.LoginInfo;
import org.example.session.Session;
import org.example.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles the Snowflake session lifecycle: login, token renewal, heartbeat and logout.
 */
@RestController
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionManager sessionManager;
    private final MockSnowflakeProperties properties;

    public SessionController(SessionManager sessionManager, MockSnowflakeProperties properties) {
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @PostMapping("/session/v1/login-request")
    public Map<String, Object> login(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(name = "databaseName", required = false) String databaseName,
            @RequestParam(name = "schemaName", required = false) String schemaName,
            @RequestParam(name = "warehouse", required = false) String warehouse,
            @RequestParam(name = "roleName", required = false) String roleName
    ) {
        Map<String, Object> data = asMap(body == null ? Map.of() : body.get("data"));
        Map<String, Object> env = asMap(data.get("CLIENT_ENVIRONMENT"));

        LoginInfo info = new LoginInfo(
                str(data.get("LOGIN_NAME")),
                firstNonNull(str(data.get("ACCOUNT_NAME")), str(env.get("account"))),
                firstNonNull(str(env.get("database")), databaseName),
                firstNonNull(str(env.get("schema")), schemaName),
                firstNonNull(str(env.get("warehouse")), warehouse),
                firstNonNull(str(env.get("role")), roleName));

        Session session = sessionManager.createSession(info);
        log.info("Login: user={} db={} schema={} -> session {}",
                session.user(), session.database(), session.schema(), session.sessionId());
        return Responses.login(session, properties);
    }

    @PostMapping("/session/token-request")
    public Map<String, Object> renew(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Map<String, Object> body) {
        String oldToken = Tokens.fromAuthorization(authorization);
        if (oldToken == null && body != null) {
            oldToken = str(body.get("oldSessionToken"));
        }
        Session session = oldToken == null ? null : sessionManager.renew(oldToken);
        if (session == null) {
            // Unknown session: hand back a fresh default so the client can keep working
            session = sessionManager.resolveOrDefault(null);
        }
        return Responses.renew(session, properties);
    }

    @PostMapping("/session/heartbeat")
    public Map<String, Object> heartbeat() { return Responses.simpleSuccess(); }

    @PostMapping("/session/authenticator-request")
    public Map<String, Object> authenticator() {
        return Responses.envelope(Map.of("tokenUrl", "", "ssoUrl", "", "proofKey", ""));
    }

    @PostMapping("/session")
    public Map<String, Object> deleteViaPost(@RequestHeader(name = "Authorization", required = false) String authorization) {
        sessionManager.close(Tokens.fromAuthorization(authorization));
        return Responses.simpleSuccess();
    }

    @DeleteMapping("/session")
    public Map<String, Object> delete(@RequestHeader(name = "Authorization", required = false) String authorization) {
        sessionManager.close(Tokens.fromAuthorization(authorization));
        return Responses.simpleSuccess();
    }
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static String str(Object value) { return value == null ? null : value.toString(); }
    private static String firstNonNull(String a, String b) { return a != null ? a : b;}
}
