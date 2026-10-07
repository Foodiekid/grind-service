package com.grindandtrain.common.publicapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
 * Rejects API requests that did not come through Cloudflare.
 * <p>
 * Cloudflare is our API gateway and adds a shared secret header to every request it forwards. A request without it
 * bypassed the WAF and rate limits, for example by calling the Cloud Run URL directly. This is the first API check,
 * so such a caller learns nothing about the API (not even which headers it expects) and gets a plain 403. Health
 * probes don't go through Cloudflare and are not checked.
 *
 * @author Dheeraj_Edupuganti
 */
@Order(FilterOrder.SERVICE)
public class CloudflareOriginFilter extends OncePerRequestFilter {

    private final byte[] secret;
    private final ProblemResponseWriter problemResponseWriter;

    /** An empty {@code grind.api.edge-secret} disables the check (local runs and tests). */
    public CloudflareOriginFilter(PublicApiProperties properties, ProblemResponseWriter problemResponseWriter) {
        String edgeSecret = properties.edgeSecret();
        this.secret = edgeSecret == null ? new byte[0] : edgeSecret.getBytes(StandardCharsets.UTF_8);
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return secret.length == 0 || !ApiPaths.isApiPath(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String sent = request.getHeader(ApiHeaders.EDGE_SECRET);
        if (sent == null || !MessageDigest.isEqual(secret, sent.getBytes(StandardCharsets.UTF_8))) {
            problemResponseWriter.write(response, PlatformErrorCode.FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
