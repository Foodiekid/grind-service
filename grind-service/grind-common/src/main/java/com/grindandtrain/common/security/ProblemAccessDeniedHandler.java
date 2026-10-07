package com.grindandtrain.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.web.ProblemResponseWriter;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers an authenticated request that isn't allowed (a path no rule permits) with 403 and the standard problem
 * body, instead of Spring Security's empty response.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public ProblemAccessDeniedHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        problemResponseWriter.write(response, PlatformErrorCode.FORBIDDEN);
    }
}
