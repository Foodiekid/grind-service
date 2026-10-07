package com.grindandtrain.common.ops;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal status page for deploys: {@code GET /internal/status} on every service's normal port (Cloud Run exposes
 * only that one, so actuator's own port can't be reached once deployed). Answers 200 when every health check passes
 * and 503 otherwise, so {@code deploy/status.sh} and CI can rely on the status code.
 * <p>
 * Internal only: Cloudflare never forwards {@code /internal/*}, and the endpoint accepts only the operator account
 * ({@link OpsProperties}, {@link OpsSecurityConfiguration}).
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class OpsStatusController {

    public static final String PATH = "/internal/status";

    private final Environment environment;
    private final ApplicationContext context;
    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<HealthEndpoint> healthEndpoint;

    public OpsStatusController(Environment environment, ApplicationContext context,
            ObjectProvider<BuildProperties> buildProperties, ObjectProvider<HealthEndpoint> healthEndpoint) {
        this.environment = environment;
        this.context = context;
        this.buildProperties = buildProperties;
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping(PATH)
    public ResponseEntity<OpsStatus> status() {
        HealthEndpoint endpoint = healthEndpoint.getIfAvailable();
        HealthDescriptor health = endpoint == null ? null : endpoint.health();
        String status = health == null ? "UNKNOWN" : health.getStatus().getCode();
        Map<String, String> checks = new TreeMap<>();
        if (health instanceof CompositeHealthDescriptor composite) {
            composite.getComponents().forEach((name, check) -> checks.put(name, check.getStatus().getCode()));
        }
        BuildProperties build = buildProperties.getIfAvailable();
        Instant startedAt = Instant.ofEpochMilli(context.getStartupDate());
        OpsStatus body = new OpsStatus(
                environment.getProperty("spring.application.name", "unknown"),
                build == null ? "unknown" : build.getVersion(),
                build == null ? null : build.getTime(),
                List.of(environment.getActiveProfiles().length > 0 ? environment.getActiveProfiles() : environment.getDefaultProfiles()),
                startedAt,
                (System.currentTimeMillis() - context.getStartupDate()) / 1000,
                status,
                checks);
        return ResponseEntity.status("UP".equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
