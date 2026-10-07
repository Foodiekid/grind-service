package com.grindandtrain.api.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.grindandtrain.common.logging.CorrelationId;
import com.grindandtrain.common.publicapi.ApiHeaders;
import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.common.web.ProblemResponseWriter;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.keyring.service.KeyringService;
import com.grindandtrain.core.sync.service.RecordService;
import com.grindandtrain.core.upload.service.UploadService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Sends requests through the real filter chain, Spring Security and the global exception handler, and checks that
 * every rejection comes back in the same problem format, in the right order: edge, client version, token, business
 * rules.
 *
 * @author Dheeraj_Edupuganti
 */
@WebMvcTest(properties = {
        "grind.public-api.edge-secret=edge-test-secret",
        "grind.public-api.min-client-versions.ios=1.2.0"})
// The public-API checks and security come from @EnableGrindPublicApi on the application class.
@Import({ProblemResponseWriter.class, ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class ApiErrorHandlingTest {

    private static final String ME = "/grind/api/v1/me";
    private static final UUID USER = UUID.fromString("3f2a8c1e-9b4d-4e7a-8f6c-2d1e0b9a7c5f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private KeyringService keyringService;
    @MockitoBean
    private RecordService recordService;
    @MockitoBean
    private UploadService uploadService;

    @Test
    void requestThatBypassedTheEdgeIsRejectedFirst() throws Exception {
        // No client header either: the edge check must answer before anything reveals the API's rules.
        mockMvc.perform(get(ME))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:grind:problem:forbidden"));
    }

    @Test
    void missingClientHeaderIsBadRequest() throws Exception {
        mockMvc.perform(get(ME).header(ApiHeaders.EDGE_SECRET, "edge-test-secret"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:client_header"));
    }

    @Test
    void outdatedAppMustUpgrade() throws Exception {
        mockMvc.perform(fromEdge(get(ME)).header(ApiHeaders.CLIENT, "ios/1.1.9"))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:upgrade_required"));
    }

    @Test
    void missingTokenIsUnauthorizedWithProblemBody() throws Exception {
        mockMvc.perform(fromApp(get(ME)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.type").value("urn:grind:problem:unauthorized"));
    }

    @Test
    void tokenWhoseSubjectIsNotAUserIsUnauthorized() throws Exception {
        mockMvc.perform(fromApp(get(ME)).with(jwt().jwt(token -> token.subject("service-account"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:unauthorized"));
    }

    @Test
    void validRequestSucceedsAndGetsACorrelationId() throws Exception {
        mockMvc.perform(signedIn(get(ME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER.toString()))
                .andExpect(header().exists(CorrelationId.HEADER));
    }

    @Test
    void callersCorrelationIdIsReturnedUnchanged() throws Exception {
        mockMvc.perform(signedIn(get(ME)).header(CorrelationId.HEADER, "app-action-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER, "app-action-1234"));
    }

    @Test
    void malformedCorrelationIdIsRejected() throws Exception {
        mockMvc.perform(signedIn(get(ME)).header(CorrelationId.HEADER, "bad id; drop table"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:correlation_id"));
    }

    @Test
    void businessErrorFromAServiceKeepsItsCode() throws Exception {
        given(keyringService.find(any())).willReturn(Optional.empty());
        mockMvc.perform(signedIn(get("/grind/api/v1/keyring")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:not_found"));
    }

    @Test
    void pathOutsideTheApiIsForbiddenWithProblemBody() throws Exception {
        mockMvc.perform(signedIn(get("/internal/anything")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:forbidden"));
    }

    @Test
    void unknownJsonFieldIsRejected() throws Exception {
        mockMvc.perform(signedIn(put("/grind/api/v1/records/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rev\":1,\"deleted\":true,\"isAdmin\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recordService);
    }

    @Test
    void duplicatedJsonFieldIsRejected() throws Exception {
        mockMvc.perform(signedIn(put("/grind/api/v1/records/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rev\":1,\"rev\":2,\"deleted\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recordService);
    }

    @Test
    void validRecordStillPasses() throws Exception {
        mockMvc.perform(signedIn(put("/grind/api/v1/records/" + UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rev\":1,\"deleted\":true}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void everyResponseCarriesTheProtectiveHeaders() throws Exception {
        // A success, a filter rejection (before Spring Security) and a security rejection.
        for (MockHttpServletRequestBuilder request : List.of(signedIn(get(ME)), get(ME), fromApp(get(ME)))) {
            mockMvc.perform(request)
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
        }
    }

    private static MockHttpServletRequestBuilder fromEdge(MockHttpServletRequestBuilder request) {
        return request.header(ApiHeaders.EDGE_SECRET, "edge-test-secret");
    }

    private static MockHttpServletRequestBuilder fromApp(MockHttpServletRequestBuilder request) {
        return fromEdge(request).header(ApiHeaders.CLIENT, "ios/1.4.2");
    }

    private static MockHttpServletRequestBuilder signedIn(MockHttpServletRequestBuilder request) {
        return fromApp(request).with(jwt().jwt(token -> token.subject(USER.toString())));
    }
}
