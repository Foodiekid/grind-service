package com.grindandtrain.common.publicapi;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.web.FilterOrder;
import com.grindandtrain.common.web.ProblemResponseWriter;

import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects request bodies without a declared length (411) or larger than {@code grind.api.max-request-bytes} (413),
 * before anything reads them.
 * <p>
 * Large data never goes through the API; blobs are uploaded straight to R2.
 *
 * @author Dheeraj_Edupuganti
 */
@Order(FilterOrder.SERVICE + 2)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ProblemResponseWriter problemResponseWriter;

    public RequestSizeLimitFilter(PublicApiProperties properties, ProblemResponseWriter problemResponseWriter) {
        this.maxBytes = properties.maxRequestBytes();
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            long length = request.getContentLengthLong();
            if (length < 0) {
                problemResponseWriter.write(response, PlatformErrorCode.LENGTH_REQUIRED);
                return;
            }
            if (length > maxBytes) {
                problemResponseWriter.write(response, PlatformErrorCode.REQUEST_TOO_LARGE);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
