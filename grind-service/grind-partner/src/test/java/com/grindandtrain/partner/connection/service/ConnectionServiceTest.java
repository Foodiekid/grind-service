package com.grindandtrain.partner.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.partner.common.error.PartnerErrorCode;
import com.grindandtrain.partner.connection.client.OAuthTokenClient;
import com.grindandtrain.partner.connection.config.PartnerProperties;
import com.grindandtrain.partner.connection.domain.PartnerId;
import com.grindandtrain.partner.connection.domain.ProviderTokens;
import com.grindandtrain.partner.connection.domain.TokenGrant;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * The token broker against a fake partner over real HTTP: what it sends (form, GRIND's credentials, PKCE verifier),
 * what it refuses before calling anyone, how provider failures and slowness are reported, and that tokens never
 * appear in a printed object.
 *
 * @author Dheeraj_Edupuganti
 */
class ConnectionServiceTest {

    private static final String REDIRECT_URI = "com.grindandtrain.app:/oauth/oura";
    private static final PartnerId OURA = PartnerId.of("oura");
    private static final String TOKENS_JSON = """
            {"access_token":"access-abc","refresh_token":"refresh-def","expires_in":86400,
             "token_type":"bearer","scope":"daily heartrate"}""";

    private final UserId user = UserId.of(UUID.randomUUID());
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<Map<String, String>> lastForm = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();

    private HttpServer provider;
    private volatile int status;
    private volatile String body;
    private volatile long delayMillis;
    private ConnectionService connectionService;

    @BeforeEach
    void setUp() throws IOException {
        status = 200;
        body = TOKENS_JSON;
        delayMillis = 0;
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.setExecutor(Executors.newCachedThreadPool());
        provider.createContext("/oauth/token", this::answer);
        provider.start();

        var oura = new PartnerProperties.ProviderSettings(
                "http://127.0.0.1:" + provider.getAddress().getPort() + "/oauth/token",
                "grind-client", "grind-secret", List.of(REDIRECT_URI),
                Duration.ofSeconds(1), Duration.ofMillis(500));
        connectionService = new ConnectionService(new PartnerProperties(Map.of("oura", oura)),
                new OAuthTokenClient(RestClient.builder()), new AuditLogger());
    }

    @AfterEach
    void tearDown() {
        provider.stop(0);
    }

    @Test
    void exchangesACodeWithGrindsCredentialsAndPkce() {
        ProviderTokens tokens = connectionService.exchange(user, OURA,
                new TokenGrant.AuthorizationCode("code-123", REDIRECT_URI, "v".repeat(43)));

        assertThat(tokens.accessToken()).isEqualTo("access-abc");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-def");
        assertThat(tokens.expiresInSeconds()).isEqualTo(86400);
        assertThat(lastContentType.get()).startsWith("application/x-www-form-urlencoded");
        assertThat(lastForm.get()).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "code-123")
                .containsEntry("redirect_uri", REDIRECT_URI)
                .containsEntry("code_verifier", "v".repeat(43))
                .containsEntry("client_id", "grind-client")
                .containsEntry("client_secret", "grind-secret");
    }

    @Test
    void refreshesAToken() {
        ProviderTokens tokens = connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old"));

        assertThat(tokens.refreshToken()).isEqualTo("refresh-def");
        assertThat(lastForm.get()).containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "refresh-old")
                .doesNotContainKey("code");
    }

    @Test
    void refusesARedirectUriGrindDidNotRegisterWithoutCallingTheProvider() {
        assertFailsWith(PartnerErrorCode.REDIRECT_URI_NOT_ALLOWED, () -> connectionService.exchange(user,
                OURA, new TokenGrant.AuthorizationCode("code-123", "https://attacker.example/cb", null)));
        assertThat(calls).hasValue(0);
    }

    @Test
    void providerRefusingTheGrantMeansConnectAgain() {
        status = 400;
        body = "{\"error\":\"invalid_grant\"}";
        assertFailsWith(PartnerErrorCode.CONNECTION_REJECTED,
                () -> connectionService.exchange(user, OURA, new TokenGrant.Refresh("used-already")));
    }

    @Test
    void providerRefusingGrindsCredentialsIsNotTheUsersFault() {
        status = 401;
        body = "{\"error\":\"invalid_client\"}";
        assertFailsWith(PartnerErrorCode.PROVIDER_UNAVAILABLE,
                () -> connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old")));
    }

    @Test
    void failingProviderIsRetryable() {
        status = 503;
        body = "";
        assertFailsWith(PartnerErrorCode.PROVIDER_UNAVAILABLE,
                () -> connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old")));
    }

    @Test
    void slowProviderTimesOut() {
        delayMillis = 2_000;
        long startedAt = System.nanoTime();
        assertFailsWith(PartnerErrorCode.PROVIDER_UNAVAILABLE,
                () -> connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old")));
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void answerWithoutAnAccessTokenIsRetryable() {
        body = "{\"token_type\":\"bearer\"}";
        assertFailsWith(PartnerErrorCode.PROVIDER_UNAVAILABLE,
                () -> connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old")));
    }

    @Test
    void providerWithoutSettingsIsNotOffered() {
        assertFailsWith(PartnerErrorCode.CONNECTION_NOT_CONFIGURED,
                () -> connectionService.exchange(user, PartnerId.of("whoop"), new TokenGrant.Refresh("refresh-old")));
    }

    @Test
    void malformedPartnerIdIsNotOffered() {
        assertFailsWith(PartnerErrorCode.CONNECTION_NOT_CONFIGURED, () -> PartnerId.of("Oura!"));
    }

    @Test
    void incompleteGrantIsRejected() {
        assertFailsWith(PartnerErrorCode.INVALID_CONNECTION_REQUEST,
                () -> new TokenGrant.AuthorizationCode("code-123", null, null));
        assertFailsWith(PartnerErrorCode.INVALID_CONNECTION_REQUEST, () -> new TokenGrant.Refresh(" "));
    }

    @Test
    void tokensAndSecretsNeverAppearWhenPrinted() {
        ProviderTokens tokens = connectionService.exchange(user, OURA, new TokenGrant.Refresh("refresh-old"));

        assertThat(tokens.toString()).doesNotContain("access-abc", "refresh-def");
        assertThat(new TokenGrant.Refresh("refresh-old").toString()).doesNotContain("refresh-old");
        assertThat(new TokenGrant.AuthorizationCode("code-123", REDIRECT_URI, null).toString()).doesNotContain("code-123");
        assertThat(new PartnerProperties.ProviderSettings("u", "id", "grind-secret", List.of("r"),
                Duration.ofSeconds(1), Duration.ofSeconds(1)).toString()).doesNotContain("grind-secret");
    }

    private void answer(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        lastForm.set(parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
        }
    }

    private static Map<String, String> parseForm(String form) {
        Map<String, String> values = new HashMap<>();
        for (String pair : form.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }
        return values;
    }

    private static void assertFailsWith(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(GrindException.class)
                .extracting(e -> ((GrindException) e).errorCode())
                .isEqualTo(expected);
    }
}
