package com.grindandtrain.common.web;

/**
 * Health probe paths on each service's public port. Cloud Run and the uptime checks call them; they need no token.
 * Everything else under actuator is on the private management port.
 *
 * @author Dheeraj_Edupuganti
 */
public final class ProbePaths {

    public static final String LIVENESS = "/livez";
    public static final String READINESS = "/readyz";

    private ProbePaths() {
    }

    /** Probes and actuator calls, which are logged at debug level so they don't drown out real traffic. */
    public static boolean isHealthCheck(String requestUri) {
        return LIVENESS.equals(requestUri) || READINESS.equals(requestUri) || requestUri.startsWith("/actuator");
    }
}
