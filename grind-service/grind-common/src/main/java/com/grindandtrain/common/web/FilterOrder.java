package com.grindandtrain.common.web;

import org.springframework.core.Ordered;

/**
 * Order of the servlet filters. All of them run before Spring Security's filter chain (order -100).
 * <p>
 * Request logging is outermost, so even a request rejected by a later filter gets its access-log line. Correlation
 * comes next, so every later filter and log line has the id. A service's own filters start at {@link #SERVICE}.
 *
 * @author Dheeraj_Edupuganti
 */
public final class FilterOrder {

    public static final int REQUEST_LOGGING = Ordered.HIGHEST_PRECEDENCE;
    public static final int CORRELATION_ID = Ordered.HIGHEST_PRECEDENCE + 1;
    public static final int SERVICE = Ordered.HIGHEST_PRECEDENCE + 10;

    private FilterOrder() {
    }
}
