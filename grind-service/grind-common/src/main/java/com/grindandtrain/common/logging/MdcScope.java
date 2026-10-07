package com.grindandtrain.common.logging;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;

/**
 * Puts fields on every log line written inside a block and restores the previous values when the block ends, even
 * when it throws. Restoring (rather than removing) matters on pooled and virtual threads, where a leftover field
 * would end up on someone else's log lines.
 *
 * <pre>{@code
 * try (MdcScope scope = MdcScope.open().put(LogFields.EVENT_ID, eventId)) {
 *     ...
 * }
 * }</pre>
 *
 * @author Dheeraj_Edupuganti
 */
public final class MdcScope implements AutoCloseable {

    private final Map<String, String> previous = new HashMap<>();

    private MdcScope() {
    }

    public static MdcScope open() {
        return new MdcScope();
    }

    /** Sets a field until {@link #close()}. A null value leaves the field as it is. */
    public MdcScope put(String key, String value) {
        if (value == null) {
            return this;
        }
        if (!previous.containsKey(key)) {
            previous.put(key, MDC.get(key));
        }
        MDC.put(key, value);
        return this;
    }

    @Override
    public void close() {
        previous.forEach((key, value) -> {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        });
    }
}
