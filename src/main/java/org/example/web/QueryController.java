package org.example.web;

import org.example.engine.MockSqlException;
import org.example.engine.QueryExecutor;
import org.example.engine.QueryOutcome;
import org.example.protocol.Responses;
import org.example.session.SessionManager;
import org.example.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Handles query execution and abort requests.
 */
@RestController
public class QueryController {
    private static final Logger log = LoggerFactory.getLogger(QueryController.class);

    private final SessionManager sessionManager;
    private final QueryExecutor queryExecutor;

    public QueryController(SessionManager sessionManager, QueryExecutor queryExecutor) {
        this.sessionManager = sessionManager;
        this.queryExecutor = queryExecutor;
    }

    @PostMapping("/queries/v1/query-request")
    public Map<String, Object> query(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @RequestBody Map<String, Object> body) {
        Session session = sessionManager.resolveOrDefault(Tokens.fromAuthorization(authorization));
        String sqlText = str(body.get("sqlText"));
        Map<String, Object> bindings = asMap(body.get("bindings"));
        boolean describeOnly = Boolean.TRUE.equals(body.get("describeOnly"));

        log.debug("Query (describeOnly: {}): {}", describeOnly, sqlText);
        try {
            QueryOutcome outcome = queryExecutor.execute(session, sqlText, bindings, describeOnly);
            return Responses.query(outcome, session);
        } catch (MockSqlException e) {
            log.debug("Query failed: {}", e.getMessage());
            return Responses.error(e, UUID.randomUUID().toString());
        }
    }

    @PostMapping("/queries/v1/abort-request")
    public Map<String, Object> abort() { return Responses.simpleSuccess(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value){
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
