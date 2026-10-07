package com.grindandtrain.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.web.ProblemResponseWriter;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers a request with a missing, expired or invalid token: 401 with the standard {@code WWW-Authenticate} header
 * (RFC 6750) and the same problem body as every other error. Without it Spring Security would send an empty 401.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        bearerEntryPoint.commence(request, response, e);
        problemResponseWriter.write(response, PlatformErrorCode.UNAUTHORIZED);
    }
}
