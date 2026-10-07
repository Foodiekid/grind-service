package com.grindandtrain.common.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.grindandtrain.common.error.ErrorCategory;
import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.error.GrindException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checks that every kind of failure becomes the same RFC 9457 problem shape, with the right status and nothing the
 * caller sent echoed back.
 *
 * @author Dheeraj_Edupuganti
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController(), validated(new PartnerController()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void grindExceptionKeepsItsCodeTitleAndCategoryStatus() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:grind:problem:test_conflict"))
                .andExpect(jsonPath("$.title").value("Changed meanwhile"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value(org.hamcrest.Matchers.startsWith("urn:grind:request:")))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void validationNamesTheFieldAndRuleButNotTheValue() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"count\":-123456789,\"name\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:validation_failed"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'count')].rule").value("Min"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].rule").value("NotNull"))
                .andExpect(content().string(not(containsString("-123456789"))));
    }

    @Test
    void malformedJsonGetsAGenericBadRequest() throws Exception {
        mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"count\": secret-value"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:bad_request"))
                .andExpect(content().string(not(containsString("secret-value"))));
    }

    @Test
    void parameterBreakingTheContractIsABadRequestNamingTheParameter() throws Exception {
        mockMvc.perform(get("/partners/Not_A_Partner"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:validation_failed"))
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.errors[0].rule").value("Pattern"))
                .andExpect(content().string(not(containsString("Not_A_Partner"))));
    }

    @Test
    void databaseOutageIsRetryable() throws Exception {
        mockMvc.perform(get("/database-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", GlobalExceptionHandler.RETRY_AFTER_SECONDS))
                .andExpect(jsonPath("$.type").value("urn:grind:problem:unavailable"));
    }

    @Test
    void unexpectedExceptionIsAGeneric500() throws Exception {
        mockMvc.perform(get("/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:internal"))
                .andExpect(jsonPath("$.title").value("Something went wrong"))
                .andExpect(content().string(not(containsString("user@example.com"))));
    }

    @Test
    void wrongMethodKeepsTheFrameworkStatus() throws Exception {
        mockMvc.perform(post("/bug"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:grind:problem:method_not_allowed"));
    }

    enum TestErrorCode implements ErrorCode {
        TEST_CONFLICT;

        @Override
        public ErrorCategory category() {
            return ErrorCategory.CONFLICT;
        }

        @Override
        public String code() {
            return "test_conflict";
        }

        @Override
        public String title() {
            return "Changed meanwhile";
        }
    }

    record Body(@NotNull @Min(0) Integer count, @NotNull String name) {
    }

    /** Wraps a controller the way Spring does for {@code @Validated} beans, so parameter constraints are checked. */
    private static Object validated(Object controller) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.afterPropertiesSet();
        return processor.postProcessAfterInitialization(controller, "controller");
    }

    @RestController
    @Validated
    static class PartnerController {

        @GetMapping("/partners/{id}")
        public String partner(@PathVariable("id") @Pattern(regexp = "^[a-z][a-z0-9-]+$") String id) {
            return id;
        }
    }

    @RestController
    static class FailingController {

        @GetMapping("/conflict")
        void conflict() {
            throw new GrindException(TestErrorCode.TEST_CONFLICT);
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Body body) {
        }

        @GetMapping("/database-down")
        void databaseDown() {
            throw new QueryTimeoutException("timeout");
        }

        @GetMapping("/bug")
        void bug() {
            throw new IllegalStateException("broken for user@example.com");
        }
    }
}
