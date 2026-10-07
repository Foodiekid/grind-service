package com.grindandtrain.worker.common.messaging;

import org.springframework.core.Ordered;

/**
 * {@code @Order} values for handlers that share an event type. Data clean-up runs first, the step that closes the work
 * runs last.
 *
 * @author Dheeraj_Edupuganti
 */
public final class HandlerOrder {

    public static final int DATA_CLEANUP = 0;
    public static final int COMPLETION = Ordered.LOWEST_PRECEDENCE;

    private HandlerOrder() {
    }
}
