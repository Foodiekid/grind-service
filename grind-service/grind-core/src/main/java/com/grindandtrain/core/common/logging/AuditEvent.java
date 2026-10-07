package com.grindandtrain.core.common.logging;

import com.grindandtrain.common.logging.AuditAction;

/**
 * Security-relevant actions of the core features, always written to the audit log whatever the log level elsewhere.
 *
 * @author Dheeraj_Edupuganti
 */
public enum AuditEvent implements AuditAction {

    ACCOUNT_DELETION_REQUESTED,
    ACCOUNT_DELETION_COMPLETED,
    ACCOUNT_DELETION_RESUMED,
    KEYRING_CREATED,
    KEYRING_REPLACED
}
