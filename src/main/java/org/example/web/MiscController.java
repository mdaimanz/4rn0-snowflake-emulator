package org.example.web;

import org.example.protocol.Responses;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Catch-all endpoints the driver may call: telemetry, monitoring and health checks.
 */
@RestController
public class MiscController {

    @PostMapping("/telemetry/send")
    public Map<String, Object> telemetry(@RequestBody(required = false) Object body) {
        return Responses.simpleSuccess();
    }

    @PostMapping("/telemetry/v1/send")
    public Map<String, Object> telemetryV1(@RequestBody(required = false) Object body) {
        return Responses.simpleSuccess();
    }

    @RequestMapping("/monitoring/queries/**")
    public Map<String, Object> monitoring() {
        return Responses.envelope(Map.of("queries", List.of()));
    }

    @GetMapping({"/", "/healthcheck", "/heath"})
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "arno-mock-snowflake");
    }
}
