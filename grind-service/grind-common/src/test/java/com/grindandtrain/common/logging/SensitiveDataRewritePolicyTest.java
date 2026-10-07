package com.grindandtrain.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

/**
 * Checks that everything sensitive is masked before a log line is written, in messages and in exceptions.
 *
 * @author Dheeraj_Edupuganti
 */
class SensitiveDataRewritePolicyTest {

    private static final String JWT = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJlc2lnbmF0dXJl";
    private static final String USER_ID = "3f2a8c1e-9b4d-4e7a-8f6c-2d1e0b9a7c5f";

    @Test
    void masksTokensEmailsIdsAndCiphertext() {
        String masked = SensitiveDataRewritePolicy.mask("token " + JWT + " auth Bearer abc.def-123 mail jo.doe@example.com"
                + " user " + USER_ID + " url ?X-Amz-Signature=deadbeef&X-Amz-Credential=AKIA/x data " + "A".repeat(240));

        assertThat(masked)
                .doesNotContain(JWT, "abc.def-123", "jo.doe@example.com", USER_ID, "deadbeef", "AKIA/x", "A".repeat(200))
                .contains("[jwt]", "Bearer [redacted]", "[email]", "[uuid]", "X-Amz-Signature=[redacted]", "[base64]");
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String message = "GET /grind/api/v1/records 200 12ms";
        assertThat(SensitiveDataRewritePolicy.mask(message)).isEqualTo(message);
    }

    @Test
    void masksExceptionMessagesAndCausesButKeepsStackFrames() {
        IllegalStateException cause = new IllegalStateException("row for " + USER_ID + " failed");
        RuntimeException thrown = new RuntimeException("request from jo.doe@example.com", cause);
        LogEvent event = Log4jLogEvent.newBuilder()
                .setLevel(Level.ERROR)
                .setMessage(new SimpleMessage("Unhandled exception"))
                .setThrown(thrown)
                .build();

        Throwable masked = SensitiveDataRewritePolicy.create().rewrite(event).getThrown();

        assertThat(masked.toString()).isEqualTo("java.lang.RuntimeException: request from [email]");
        assertThat(masked.getStackTrace()).isEqualTo(thrown.getStackTrace());
        assertThat(masked.getCause().toString()).isEqualTo("java.lang.IllegalStateException: row for [uuid] failed");
    }

    @Test
    void returnsTheSameEventWhenNothingNeedsMasking() {
        LogEvent event = Log4jLogEvent.newBuilder().setLevel(Level.INFO).setMessage(new SimpleMessage("Published event")).build();
        assertThat(SensitiveDataRewritePolicy.create().rewrite(event)).isSameAs(event);
    }
}
