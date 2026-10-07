package com.grindandtrain.common.publicapi;

import java.util.List;

import com.grindandtrain.common.logging.CorrelationId;

/**
 * Names of the custom headers used by the public API.
 *
 * @author Dheeraj_Edupuganti
 */
public final class ApiHeaders {

    /** Required on every API request, as {@code <ios|android|web>/<major>.<minor>.<patch>}. */
    public static final String CLIENT = "X-Grind-Client";

    /** Tracking id of one user action. Optional on requests (generated when absent), always set on responses. */
    public static final String CORRELATION_ID = CorrelationId.HEADER;

    /** Set by Cloudflare only; proves the request came through the edge. */
    public static final String EDGE_SECRET = "X-Grind-Edge";

    /** RFC 9745. */
    public static final String DEPRECATION = "Deprecation";

    /** RFC 8594. */
    public static final String SUNSET = "Sunset";

    public static final List<String> ALLOWED_REQUEST_HEADERS = List.of("Authorization", "Content-Type", CLIENT, CORRELATION_ID);

    public static final List<String> EXPOSED_RESPONSE_HEADERS = List.of(CORRELATION_ID, DEPRECATION, SUNSET);

    private ApiHeaders() {
    }
}
