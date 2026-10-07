package com.grindandtrain.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.logging.CorrelationId;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Starts the trace of a request. Takes the caller's {@value CorrelationId#HEADER} when it is well formed, creates one
 * when it is absent, and rejects a malformed one with 400. The id is returned on the response and put on every log
 * line written while the request is handled.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@Order(FilterOrder.CORRELATION_ID)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final ProblemResponseWriter problemResponseWriter;

    public CorrelationIdFilter(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (correlationId == null) {
            correlationId = CorrelationId.newId();
        } else if (!CorrelationId.isValid(correlationId)) {
            problemResponseWriter.write(response, PlatformErrorCode.CORRELATION_ID);
            return;
        }
        response.setHeader(CorrelationId.HEADER, correlationId);
        RequestLoggingFilter.remember(request, LogFields.CORRELATION_ID, correlationId);
        try (MdcScope scope = MdcScope.open().put(LogFields.CORRELATION_ID, correlationId)) {
            chain.doFilter(request, response);
        }
    }
}
