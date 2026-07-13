package org.example;

import org.example.config.MockSnowflakeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Snowflake mock server
 *
 * <p>The server emulates the subset of the Snowflake REST API that JDBC / driver clients use
 * (login, query, session lifecycle) and executes the submitted SQL against an embedded DuckDB
 * instance. It is meant to be used as a Localstack-style test double during integration testing
 */
@SpringBootApplication
@EnableConfigurationProperties(MockSnowflakeProperties.class)
public class MockSnowflakeApplication {
    public static void main(String[] args) { SpringApplication.run(MockSnowflakeApplication.class, args); }
}
