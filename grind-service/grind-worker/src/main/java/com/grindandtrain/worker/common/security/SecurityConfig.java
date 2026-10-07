package com.grindandtrain.worker.common.security;

import java.util.List;

import com.grindandtrain.common.security.GoogleIdTokens;
import com.grindandtrain.common.security.GrindHttpSecurity;
import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.common.jobs.JobController;
import com.grindandtrain.worker.common.messaging.PubSubPushController;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security setup of the worker.
 * <p>
 * Only Google services may call it, each with a Google-signed OIDC token from its own service account and only on
 * its own endpoint ({@link GoogleCaller}): Pub/Sub pushes events, Cloud Scheduler starts jobs, and neither can do the
 * other's work. The worker is not behind Cloudflare and has no public API. Everything else (stateless, probes,
 * problem responses) is the shared baseline, {@link GrindHttpSecurity}.
 *
 * @author Dheeraj_Edupuganti
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain workerFilterChain(HttpSecurity http,
            WorkerProperties properties, ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler) throws Exception {
        return GrindHttpSecurity.baseline(http,
                        GoogleIdTokens.decoder(token -> GoogleCaller.identify(token, properties).isPresent()), authenticationEntryPoint, accessDeniedHandler)
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(callerAuthorities(properties))))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, PubSubPushController.PATH).hasAuthority(GoogleCaller.PUBSUB_PUSH.authority())
                        .requestMatchers(HttpMethod.POST, JobController.PATH).hasAuthority(GoogleCaller.SCHEDULER.authority())
                        .anyRequest().denyAll())
                .build();
    }

    private static JwtAuthenticationConverter callerAuthorities(WorkerProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(token -> GoogleCaller.identify(token, properties)
                .map(caller -> List.<GrantedAuthority>of(
                        new SimpleGrantedAuthority(caller.authority())))
                .orElse(List.of()));
        return converter;
    }
}
