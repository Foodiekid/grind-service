package com.grindandtrain.common.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.security.GrindHttpSecurity;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem response straight to the servlet response, for code that runs outside Spring MVC: servlet
 * filters, and Spring Security's entry point and access-denied handler. Produces the same JSON as the global
 * exception handler, so the app sees one error format whatever rejected the request. These responses are written
 * before Spring Security runs, so they set its protective headers themselves.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ErrorCode error) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        HttpStatus status = ProblemDetailFactory.statusOf(error.category());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", ProblemDetailFactory.TYPE_PREFIX + error.code());
        body.put("title", error.title());
        body.put("status", status.value());
        body.put("instance", ProblemDetailFactory.currentInstance());
        response.setStatus(status.value());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", GrindHttpSecurity.CONTENT_SECURITY_POLICY);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
