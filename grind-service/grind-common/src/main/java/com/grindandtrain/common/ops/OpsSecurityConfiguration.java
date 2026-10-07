package com.grindandtrain.common.ops;

import java.util.List;

import com.grindandtrain.common.security.GoogleIdTokens;
import com.grindandtrain.common.security.GrindHttpSecurity;
import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for {@code /internal/status} in every service: its own filter chain, checked before the service's main
 * one, accepting only a Google token from the operator account ({@code grind.ops}). Nobody else, including a signed-in
 * app user, Pub/Sub or Scheduler, can read it.
 *
 * @author Dheeraj_Edupuganti
 */
@Configuration
@EnableConfigurationProperties(OpsProperties.class)
public class OpsSecurityConfiguration {

    public static final String OPERATOR = "operator";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain opsStatusFilterChain(HttpSecurity http, OpsProperties ops,
            ProblemAuthenticationEntryPoint authenticationEntryPoint, ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        JwtAuthenticationConverter operatorAuthority = new JwtAuthenticationConverter();
        operatorAuthority.setJwtGrantedAuthoritiesConverter(token -> ops.isOperator(token)
                ? List.<GrantedAuthority>of(new SimpleGrantedAuthority(OPERATOR))
                : List.of());
        return GrindHttpSecurity.baseline(http.securityMatcher(OpsStatusController.PATH),
                        GoogleIdTokens.decoder(ops::isOperator), authenticationEntryPoint, accessDeniedHandler)
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(operatorAuthority)))
                .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority(OPERATOR))
                .build();
    }
}
