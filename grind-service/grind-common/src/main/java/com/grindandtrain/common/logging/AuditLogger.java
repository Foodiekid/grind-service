package com.grindandtrain.common.logging;

import com.grindandtrain.common.domain.UserId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes security-relevant actions to the {@code com.grindandtrain.audit} logger, so they can be routed and kept apart
 * from ordinary application logs. Only the action, the user's pseudonym and an optional short, non-personal detail are
 * recorded, never any content. Each service lists its own actions as an enum implementing {@link AuditAction}.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("com.grindandtrain.audit");

    public void record(AuditAction action, UserId userId) {
        try (MdcScope scope = MdcScope.open().put(LogFields.USER, userId.pseudonym())) {
            log.info("{}", action.name());
        }
    }

    /** With a short, non-personal detail, such as which provider was connected. */
    public void record(AuditAction action, UserId userId, String detail) {
        try (MdcScope scope = MdcScope.open().put(LogFields.USER, userId.pseudonym())) {
            log.info("{} {}", action.name(), detail);
        }
    }
}
