package com.grindandtrain.common.publicapi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;
import com.grindandtrain.common.web.FilterOrder;
import com.grindandtrain.common.web.ProblemResponseWriter;
import com.grindandtrain.common.web.RequestLoggingFilter;

import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks the {@code X-Grind-Client} header on every public API request.
 * <p>
 * A missing or malformed header gets 400 {@code client_header}. A build older than the minimum configured for its
 * platform ({@code grind.api.min-client-versions}) gets 426 {@code upgrade_required}, which is how an old app version
 * is retired cleanly. CORS preflight requests are let through.
 *
 * @author Dheeraj_Edupuganti
 */
@Order(FilterOrder.SERVICE + 1)
public class ClientVersionFilter extends OncePerRequestFilter {

    private final Map<String, ClientVersion> minimumVersions = new HashMap<>();
    private final ProblemResponseWriter problemResponseWriter;

    public ClientVersionFilter(PublicApiProperties properties, ProblemResponseWriter problemResponseWriter) {
        properties.minClientVersions()
                .forEach((platform, version) -> minimumVersions.put(platform, ClientVersion.of(platform, version)));
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equals(request.getMethod()) || !ApiPaths.isApiPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ClientVersion> client = ClientVersion.parse(request.getHeader(ApiHeaders.CLIENT));
        if (client.isEmpty()) {
            problemResponseWriter.write(response, PlatformErrorCode.CLIENT_HEADER);
            return;
        }
        String clientName = client.get().toString();
        RequestLoggingFilter.remember(request, LogFields.CLIENT, clientName);
        ClientVersion minimum = minimumVersions.get(client.get().platform());
        if (minimum != null && client.get().compareTo(minimum) < 0) {
            problemResponseWriter.write(response, PlatformErrorCode.UPGRADE_REQUIRED);
            return;
        }
        try (MdcScope scope = MdcScope.open().put(LogFields.CLIENT, clientName)) {
            chain.doFilter(request, response);
        }
    }
}
