package com.grindandtrain.common.ops;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What {@code /internal/status} reports about one running service, for checking a deploy. Facts about the service
 * only: no settings values, secrets or user data.
 *
 * @param status UP when every health check passes; otherwise the worst check's status
 * @param checks each health check's status, e.g. {@code db -> UP}
 *
 * @author Dheeraj_Edupuganti
 */
public record OpsStatus(
        String service,
        String version,
        Instant builtAt,
        List<String> profiles,
        Instant startedAt,
        long uptimeSeconds,
        String status,
        Map<String, String> checks) {
}
