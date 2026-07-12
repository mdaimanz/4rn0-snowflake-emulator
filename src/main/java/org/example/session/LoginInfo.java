package org.example.session;

/**
 * Connection context extracted from a Snowflake login request. Any field may be {@code null}.
 */
public record LoginInfo(String user,
                        String account,
                        String database,
                        String schema,
                        String warehouse,
                        String role){
}
