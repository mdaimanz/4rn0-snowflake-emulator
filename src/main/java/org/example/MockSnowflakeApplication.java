package org.example;

import org.example.config.MockSnowflakeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockSnowflakeProperties.class)
public class MockSnowflakeApplication {
    public static void main(String[] args) { SpringApplication.run(MockSnowflakeApplication.class, args); }
}
