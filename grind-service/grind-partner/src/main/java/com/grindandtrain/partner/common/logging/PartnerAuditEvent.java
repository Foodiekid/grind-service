package com.grindandtrain.partner.common.logging;

import com.grindandtrain.common.logging.AuditAction;

/**
 * Security-relevant actions of the partner service, always written to the audit log.
 *
 * @author Dheeraj_Edupuganti
 */
public enum PartnerAuditEvent implements AuditAction {

    CONNECTION_AUTHORIZED
}
