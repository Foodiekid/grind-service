package com.grindandtrain.common.logging;

/**
 * A security-relevant action written to the audit log. Implemented by one enum per service, the same way
 * {@link com.grindandtrain.common.error.ErrorCode} is; names are stable once logged.
 *
 * @author Dheeraj_Edupuganti
 */
public interface AuditAction {

    String name();
}
