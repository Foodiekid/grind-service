package com.grindandtrain.common.web;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes one access-log line per request once the response is complete: method, path, status and duration. Never
 * the query string, headers or body.
 * <p>
 * It runs outermost, so requests rejected by later filters are logged too. Those filters record what they learned
 * (correlation id, client, user) with {@link #remember}, and this filter puts it on the line. Health checks are
 * logged at debug level only.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@Order(FilterOrder.REQUEST_LOGGING)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.grindandtrain.access");

    private static final String ATTRIBUTE_PREFIX = RequestLoggingFilter.class.getName() + ".";
    private static final List<String> FIELDS = List.of(LogFields.CORRELATION_ID, LogFields.CLIENT, LogFields.USER);

    /** Records a log field learned while handling the request, for the access-log line. */
    public static void remember(HttpServletRequest request, String field, String value) {
        request.setAttribute(ATTRIBUTE_PREFIX + field, value);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            try (MdcScope scope = rememberedFields(request)) {
                if (ProbePaths.isHealthCheck(request.getRequestURI())) {
                    log.debug("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
                } else {
                    log.info("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
                }
            }
        }
    }

    private static MdcScope rememberedFields(HttpServletRequest request) {
        MdcScope scope = MdcScope.open();
        for (String field : FIELDS) {
            Object value = request.getAttribute(ATTRIBUTE_PREFIX + field);
            scope.put(field, value == null ? null : value.toString());
        }
        return scope;
    }
}
