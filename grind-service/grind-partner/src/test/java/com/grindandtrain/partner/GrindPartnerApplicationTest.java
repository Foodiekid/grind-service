package com.grindandtrain.partner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.grindandtrain.common.ops.OpsSecurityConfiguration;
import com.grindandtrain.common.publicapi.CloudflareOriginFilter;
import com.grindandtrain.partner.connection.service.ConnectionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

/**
 * Starts the whole partner service with its local settings, as it would run on a developer machine. It needs no
 * database, so this proves the real wiring end to end: shared defaults, the public-API bundle, the broker, the health
 * probes and the internal status page with its operator-only security.
 *
 * @author Dheeraj_Edupuganti
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class GrindPartnerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void startsWithTheSharedPublicApiAndTheBroker() {
        assertThat(context.getBean(ConnectionService.class)).isNotNull();
        assertThat(context.getBean(CloudflareOriginFilter.class)).isNotNull();
        assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
    }

    @Test
    void probesAreOpenForCloudRun() throws Exception {
        mockMvc.perform(get("/livez")).andExpect(status().isOk());
        mockMvc.perform(get("/readyz")).andExpect(status().isOk());
    }

    @Test
    void statusPageReportsTheServiceToAnOperator() throws Exception {
        mockMvc.perform(get("/internal/status").with(jwt().authorities(new SimpleGrantedAuthority(OpsSecurityConfiguration.OPERATOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("grind-partner"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.readinessState").value("UP"))
                .andExpect(jsonPath("$.profiles[0]").value("local"));
    }

    @Test
    void statusPageIsClosedToEveryoneElse() throws Exception {
        mockMvc.perform(get("/internal/status")).andExpect(status().isUnauthorized());
        // A signed-in app user is not an operator.
        mockMvc.perform(get("/internal/status").with(jwt())).andExpect(status().isForbidden());
    }
}
