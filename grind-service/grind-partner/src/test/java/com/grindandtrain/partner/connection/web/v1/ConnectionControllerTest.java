package com.grindandtrain.partner.connection.web.v1;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.common.publicapi.ApiHeaders;
import com.grindandtrain.common.web.ProblemResponseWriter;
import com.grindandtrain.partner.connection.domain.PartnerId;
import com.grindandtrain.partner.connection.domain.ProviderTokens;
import com.grindandtrain.partner.connection.domain.TokenGrant;
import com.grindandtrain.partner.connection.service.ConnectionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The token broker endpoint through the real filters and security: request mapping, validation, and that token
 * responses are never cached.
 *
 * @author Dheeraj_Edupuganti
 */
@WebMvcTest(controllers = ConnectionController.class)
// The public-API checks and security come from @EnableGrindPublicApi on the application class.
@Import({ProblemResponseWriter.class, ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class ConnectionControllerTest {

    private static final String OURA_TOKEN = "/grind/api/v1/connections/oura/token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConnectionService connectionService;

    @Test
    void exchangesACodeAndForbidsCaching() throws Exception {
        given(connectionService.exchange(any(), eq(PartnerId.of("oura")), any(TokenGrant.AuthorizationCode.class)))
                .willReturn(new ProviderTokens("access-abc", "refresh-def", 86400, "bearer", "daily"));

        mockMvc.perform(signedIn(post(OURA_TOKEN)).content("""
                        {"grantType":"authorization_code","code":"code-123",
                         "redirectUri":"com.grindandtrain.app:/oauth/oura"}"""))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.accessToken").value("access-abc"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-def"))
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }

    @Test
    void malformedPartnerIdIsABadRequest() throws Exception {
        mockMvc.perform(signedIn(post("/grind/api/v1/connections/Not_A_Partner/token"))
                        .content("{\"grantType\":\"refresh_token\",\"refreshToken\":\"r\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(connectionService);
    }

    @Test
    void invalidRequestNamesTheFieldNotTheValue() throws Exception {
        mockMvc.perform(signedIn(post(OURA_TOKEN))
                        .content("{\"grantType\":\"refresh_token\",\"codeVerifier\":\"too-short-secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:validation_failed"))
                .andExpect(content().string(not(containsString("too-short-secret"))));
        verifyNoInteractions(connectionService);
    }

    @Test
    void requiresSignIn() throws Exception {
        mockMvc.perform(post(OURA_TOKEN).header(ApiHeaders.CLIENT, "ios/1.0.0")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grantType\":\"refresh_token\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(connectionService);
    }

    private static MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
        return request.header(ApiHeaders.CLIENT, "ios/1.0.0")
                .contentType(MediaType.APPLICATION_JSON)
                .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString())));
    }
}
