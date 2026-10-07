package com.grindandtrain.common.publicapi;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.GrindHttpSecurity;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.common.web.ProblemResponseWriter;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Everything a service needs to serve the public API under {@code /grind/api}, switched on with
 * {@link EnableGrindPublicApi}. Shared by grind-api and grind-partner, so both check requests the same way.
 * <p>
 * Filters, in order (see FilterOrder): Cloudflare edge secret, then the {@code X-Grind-Client} version, then the body
 * size, then deprecation headers. Then Spring Security on the shared baseline ({@link GrindHttpSecurity}): every
 * version needs a Supabase access token, anything else is denied. Signing keys are fetched on first use, so start-up
 * doesn't need the network.
 * <p>
 * Deliberately not a scanned {@code @Configuration}: services that scan {@code com.grindandtrain.common} (grind-worker)
 * must not get a public API by accident.
 *
 * @author Dheeraj_Edupuganti
 */
@EnableConfigurationProperties(PublicApiProperties.class)
public class PublicApiConfiguration {

    private static final String SUPABASE_AUDIENCE = "authenticated";

    @Bean
    CloudflareOriginFilter cloudflareOriginFilter(PublicApiProperties properties, ProblemResponseWriter writer) {
        return new CloudflareOriginFilter(properties, writer);
    }

    @Bean
    ClientVersionFilter clientVersionFilter(PublicApiProperties properties, ProblemResponseWriter writer) {
        return new ClientVersionFilter(properties, writer);
    }

    @Bean
    RequestSizeLimitFilter requestSizeLimitFilter(PublicApiProperties properties, ProblemResponseWriter writer) {
        return new RequestSizeLimitFilter(properties, writer);
    }

    @Bean
    ApiDeprecationFilter apiDeprecationFilter(PublicApiProperties properties) {
        return new ApiDeprecationFilter(properties);
    }

    /**
     * How the public API reads requests.
     * <ul>
     *   <li>Request bodies are strict: an unknown or duplicated JSON field is a 400, so a typo or a tampered payload
     *   is refused instead of silently ignored. Only for requests from the app: outbound clients and the worker keep
     *   the lenient reader, because Google, Oura and WHOOP may add fields at any time.</li>
     *   <li>Path and query enums convert by their contract value ({@code oura}), not the Java constant name.</li>
     * </ul>
     */
    @Bean
    WebMvcConfigurer publicApiRequestReading(JsonMapper jsonMapper) {
        JsonMapper strict = jsonMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        return new WebMvcConfigurer() {
            @Override
            public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
                builder.withJsonConverter(new JacksonJsonHttpMessageConverter(strict));
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverterFactory(new ContractEnumConverterFactory());
            }
        };
    }

    @Bean
    SecurityFilterChain publicApiFilterChain(HttpSecurity http, PublicApiProperties properties,
            ProblemAuthenticationEntryPoint authenticationEntryPoint, ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        PublicApiProperties.Auth auth = properties.auth();
        return GrindHttpSecurity.baseline(http, supabaseDecoder(auth.supabaseJwksUri(), auth.supabaseIssuer()),
                        authenticationEntryPoint, accessDeniedHandler)
                .cors(Customizer.withDefaults())
                .addFilterAfter(new AuthenticatedUserLoggingFilter(), BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(ApiPaths.ALL_VERSIONS).authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(PublicApiProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cors.setAllowedHeaders(ApiHeaders.ALLOWED_REQUEST_HEADERS);
        cors.setExposedHeaders(ApiHeaders.EXPOSED_RESPONSE_HEADERS);
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPaths.ALL_VERSIONS, cors);
        return source;
    }

    /** Supabase access tokens: asymmetric signing keys, the project's issuer, audience "authenticated". */
    private static JwtDecoder supabaseDecoder(String jwksUri, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(SUPABASE_AUDIENCE))));
        return decoder;
    }
}
