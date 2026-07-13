package org.example.web;

/**
 * Parses the Snowflake {@code Authorization: Snowflake Token="...."} header.
 */
public class Tokens {

    private Tokens(){}

    static String fromAuthorization(String authorization) {
        if (authorization == null) {
            return null;
        }
        int marker = authorization.indexOf("Token=");
        if (marker < 0) {
            return null;
        }
        String token = authorization.substring(marker + "Token=".length()).trim();
        if (token.startsWith("\"")) {
            token = token.substring(1);
        }
        if (token.endsWith("\"")) {
            token = token.substring(0, token.length() -1);
        }
        return token.isEmpty() ? null : token;
    }
}
