package com.grindandtrain.worker.common.jobs;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.grindandtrain.common.config.ClockConfig;
import com.grindandtrain.common.security.ProblemAccessDeniedHandler;
import com.grindandtrain.common.security.ProblemAuthenticationEntryPoint;
import com.grindandtrain.common.web.ProblemResponseWriter;
import com.grindandtrain.worker.common.config.WorkerProperties;
import com.grindandtrain.worker.common.security.GoogleCaller;
import com.grindandtrain.worker.common.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Starting jobs through the real security: only Cloud Scheduler's token may, an unknown job is a 404, and the run's
 * report comes back.
 *
 * @author Dheeraj_Edupuganti
 */
@WebMvcTest(controllers = JobController.class)
@Import({SecurityConfig.class, ClockConfig.class, ProblemResponseWriter.class, ProblemAuthenticationEntryPoint.class,
        ProblemAccessDeniedHandler.class, JobControllerTest.Jobs.class})
@EnableConfigurationProperties(WorkerProperties.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void schedulerStartsAJobAndGetsItsReport() throws Exception {
        mockMvc.perform(post("/internal/jobs/sample-job").with(as(GoogleCaller.SCHEDULER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("sample-job"))
                .andExpect(jsonPath("$.examined").value(3))
                .andExpect(jsonPath("$.changed").value(1))
                .andExpect(jsonPath("$.finished").value(true));
    }

    @Test
    void pubSubCannotStartJobs() throws Exception {
        mockMvc.perform(post("/internal/jobs/sample-job").with(as(GoogleCaller.PUBSUB_PUSH)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mockMvc.perform(post("/internal/jobs/no-such-job").with(as(GoogleCaller.SCHEDULER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:not_found"));
    }

    @Test
    void withoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/jobs/sample-job"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(GoogleCaller caller) {
        return jwt().authorities(new SimpleGrantedAuthority(caller.authority()));
    }

    @TestConfiguration
    static class Jobs {

        @Bean
        ScheduledJob sampleJob() {
            return new ScheduledJob() {
                @Override
                public String name() {
                    return "sample-job";
                }

                @Override
                public JobReport run(Instant deadline) {
                    return new JobReport(name(), 3, 1, true);
                }
            };
        }
    }
}
