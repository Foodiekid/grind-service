package com.grindandtrain.common.logging;

import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * Last line of defence for everything written to the logs, including by libraries. Before a line is written, this
 * policy blanks out tokens, signed-URL credentials, email addresses, UUIDs (user ids are UUIDs) and long base64 strings
 * (ciphertext), in the message and in the messages of a logged exception and its causes. Stack frames are kept, so a
 * 500 can still be debugged.
 * <p>
 * Our own code doesn't log any of these in the first place: ids go in {@link LogFields}, which are not masked. Wired
 * in log4j2-spring.xml.
 *
 * @author Dheeraj_Edupuganti
 */
@Plugin(name = "SensitiveDataRewritePolicy", category = Core.CATEGORY_NAME, elementType = "rewritePolicy", printObject = true)
public final class SensitiveDataRewritePolicy implements RewritePolicy {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"), "[jwt]"),
            new Rule(Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [redacted]"),
            new Rule(Pattern.compile("(?i)(X-Amz-(?:Signature|Credential|Security-Token)=)[^&\\s\"]+"), "$1[redacted]"),
            new Rule(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]"),
            new Rule(Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "[uuid]"),
            new Rule(Pattern.compile("[A-Za-z0-9+/]{200,}={0,2}"), "[base64]"));

    /** Guards against cause cycles and absurdly deep chains. */
    private static final int MAX_CAUSE_DEPTH = 16;

    @PluginFactory
    public static SensitiveDataRewritePolicy create() {
        return new SensitiveDataRewritePolicy();
    }

    @Override
    public LogEvent rewrite(LogEvent event) {
        String original = event.getMessage().getFormattedMessage();
        String masked = mask(original);
        Throwable thrown = event.getThrown();
        if (masked.equals(original) && thrown == null) {
            return event;
        }
        Log4jLogEvent.Builder builder = new Log4jLogEvent.Builder(event);
        if (!masked.equals(original)) {
            builder.setMessage(new SimpleMessage(masked));
        }
        if (thrown != null) {
            builder.setThrown(MaskedThrowable.of(thrown, 0)).setThrownProxy(null);
        }
        return builder.build();
    }

    static String mask(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String result = message;
        for (Rule rule : RULES) {
            result = rule.pattern().matcher(result).replaceAll(rule.replacement());
        }
        return result;
    }

    /**
     * A copy of a logged exception with a masked message and the original stack frames. Prints as
     * {@code <original class>: <masked message>}, like the original would.
     */
    static final class MaskedThrowable extends Throwable {

        private MaskedThrowable(String text, Throwable cause, StackTraceElement[] stackTrace) {
            super(text, cause, false, true);
            setStackTrace(stackTrace);
        }

        static MaskedThrowable of(Throwable original, int depth) {
            Throwable cause = original.getCause();
            MaskedThrowable maskedCause = cause == null || cause == original || depth >= MAX_CAUSE_DEPTH
                    ? null
                    : of(cause, depth + 1);
            String message = original.getMessage();
            String text = original.getClass().getName() + (message == null ? "" : ": " + mask(message));
            return new MaskedThrowable(text, maskedCause, original.getStackTrace());
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }
}
