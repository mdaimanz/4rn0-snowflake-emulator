package org.example.protocol;


import org.example.config.MockSnowflakeProperties;
import org.example.engine.MockSqlException;
import org.example.engine.QueryOutcome;
import org.example.engine.SnowflakeColumn;
import org.example.session.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON envelopes that Snowflake Client expect for each REST endpoint
 */
public final class Responses {

    private Responses() {}

    /**
     * Wraps a data payload in the standard {@code {data, code, message, success}} envelope
     * @param data
     * @return
     */
    public static Map<String, Object> envelope(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("code", null);
        body.put("message", null);
        body.put("success", null);
        return body;
    }

    public static Map<String, Object> login(Session session, MockSnowflakeProperties properties) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", session.token());
        data.put("masterToken", session.masterToken());
        data.put("validityInSeconds", properties.sessionValiditySeconds());
        data.put("masterValidityInSeconds", properties.masterValiditySeconds());
        data.put("displayUserName", session.user());
        data.put("serverVersion", properties.serverVersion());
        data.put("firstLogin", false);
        data.put("remMeToken", null);
        data.put("remMeValidityInSeconds", 0);
        data.put("healthCheckInterval",45);
        data.put("newClientForUpgrade", null);
        data.put("sessionId", session.sessionId());
        data.put("parameters", sessionParameters());
        data.put("sessionInfo", sessionInfo(session));
        data.put("idToken", null);
        data.put("idTokenValidityInSeconds", 0);
        data.put("responseData", null);
        data.put("mfaToken", null);
        data.put("mfaTokenValidityInSeconds", 0);
        return envelope(data);
    }

    public static Map<String, Object> renew(Session session, MockSnowflakeProperties properties) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionToken", session.token());
        data.put("validityInSecondsST", properties.sessionValiditySeconds());
        data.put("masterToken", session.masterToken());
        data.put("validityInSecondsMT", properties.masterValiditySeconds());
        data.put("sessionId", session.sessionId());
        return envelope(data);
    }

    public static Map<String, Object> query(QueryOutcome outcome, Session session) {
        List<Map<String, Object>> rowType = new ArrayList<>();
        for(SnowflakeColumn column : outcome.columns()) {
            rowType.add(column.toRowType());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("parameters", List.of());
        data.put("rowtype", rowType);
        data.put("rowset", outcome.rows());
        data.put("total", (long) outcome.rows().size());
        data.put("returned", (long) outcome.rows().size());
        data.put("queryId", outcome.queryId());
        data.put("databaseProvider", null);
        data.put("finalDatabaseName", session.database());
        data.put("finalSchemaName", session.schema());
        data.put("finalWarehouseName", session.warehouse());
        data.put("finalRoleName", session.role());
        data.put("numberOfBinds", 0);
        data.put("arrayBindSupported", false);
        data.put("statementTypeId", outcome.statementTypeId());
        data.put("version", 1);
        data.put("sendResultTime", System.currentTimeMillis());
        data.put("queryResultFormat", "json");
        data.put("chunks", null);
        return envelope(data);
    }

    public static Map<String, Object> error(MockSqlException e, String queryId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("internalError", false);
        data.put("errorCode", e.errorCode());
        data.put("sqlState", e.sqlState());
        data.put("queryId", queryId);
        data.put("line", -1);
        data.put("pos", -1);
        data.put("type", "COMPILATION");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("code", e.errorCode());
        body.put("message", e.getMessage());
        body.put("success", false);
        return body;
    }

    public static Map<String, Object> simpleSuccess() { return envelope(null); }

    private static Map<String, Object> sessionInfo(Session session) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("databaseName", session.database());
        info.put("schemaName", session.schema());
        info.put("warehouseName", session.warehouse());
        info.put("roleName", session.role());
        return info;
    }

    private static List<Map<String, Object>> sessionParameters() {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(param("TIMEZONE", "UTC"));
        params.add(param("TIMESTAMP_OUTPUT_FORMAT", "YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM"));
        params.add(param("TIMESTAMP_NTZ_OUTPUT_FORMAT","YYYY-MM-DD HH24:MI:SS.FF9"));
        params.add(param("TIMESTAMP_LTZ_OUTPUT_FORMAT", "YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM"));
        params.add(param("TIMESTAMP_TZ_OUTPUT_FORMAT","YYYY-MM-DD HH24:MI:SS.FF9 TZHTZM"));
        params.add(param("DATE_OUTPUT_FORMAT", "YYYY-MM-DD"));
        params.add(param("TIME_OUTPUT_FORMAT", "HH24:MI:SS.FF9"));
        params.add(param("BINARY_OUTPUT_FORMAT", "HEX"));
        params.add(param("AUTOCOMMIT", true));
        params.add(param("CLIENT_RESULT_CHUNK_SIZE", 160));
        params.add(param("CLIENT_PREFETCH_THREADS", 4));
        params.add(param("CLIENT_SESSION_KEEP_ALIVE", false));
        params.add(param("CLIENT_TELEMETRY_ENABLE", false));
        params.add(param("CLIENT_OUT_OF_BAND_TELEMETRY_ENABLED", false));
        return params;
    }

    private static Map<String, Object> param(String name, Object value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", name);
        p.put("value", value);
        return p;
    }
}
