package com.grindandtrain.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Checks that log fields set for a block are removed or restored afterwards, so they never leak onto another
 * request's log lines.
 *
 * @author Dheeraj_Edupuganti
 */
class MdcScopeTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void removesFieldsThatWereNotSetBefore() {
        try (MdcScope scope = MdcScope.open().put(LogFields.EVENT_ID, "e-1")) {
            assertThat(MDC.get(LogFields.EVENT_ID)).isEqualTo("e-1");
        }
        assertThat(MDC.get(LogFields.EVENT_ID)).isNull();
    }

    @Test
    void restoresThePreviousValue() {
        MDC.put(LogFields.CORRELATION_ID, "outer");
        try (MdcScope scope = MdcScope.open().put(LogFields.CORRELATION_ID, "inner")) {
            assertThat(MDC.get(LogFields.CORRELATION_ID)).isEqualTo("inner");
        }
        assertThat(MDC.get(LogFields.CORRELATION_ID)).isEqualTo("outer");
    }

    @Test
    void ignoresNullValues() {
        MDC.put(LogFields.USER, "u");
        try (MdcScope scope = MdcScope.open().put(LogFields.USER, null)) {
            assertThat(MDC.get(LogFields.USER)).isEqualTo("u");
        }
        assertThat(MDC.get(LogFields.USER)).isEqualTo("u");
    }
}
