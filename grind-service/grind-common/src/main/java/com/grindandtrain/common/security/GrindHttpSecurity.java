package com.grindandtrain.common.security;

import com.grindandtrain.common.web.ProbePaths;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * The security baseline every GRIND service starts from, so each service only states what differs: its token decoder
 * and its own rules.
 * <p>
 * Stateless (no sessions or cookies, so no CSRF protection is needed); liveness and readiness probes open; actuator
 * open because it only listens on the private management port; every request with a bearer token checked by the
 * given decoder; rejections answered in the shared problem format (401 with {@code WWW-Authenticate}, 403). On top of
 * Spring Security's default headers ({@code no-store}, {@code nosniff}, frame denial), responses forbid any content
 * from loading or framing ({@code Content-Security-Policy: default-src 'none'; frame-ancestors 'none'}) and send no
 * referrer: the API only ever returns JSON. The caller
 * adds its rules with {@code authorizeHttpRequests} and must end them with {@code anyRequest().denyAll()}.
 *
 * @author Dheeraj_Edupuganti
 */
public final class GrindHttpSecurity {

    /** JSON only: nothing may load, run or frame. */
    public static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'";

    private GrindHttpSecurity() {
    }

    public static HttpSecurity baseline(HttpSecurity http, JwtDecoder decoder,
            ProblemAuthenticationEntryPoint authenticationEntryPoint, ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, ProbePaths.LIVENESS, ProbePaths.READINESS).permitAll()
                        .requestMatchers("/actuator/**").permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(server -> server
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder)));
    }
}
