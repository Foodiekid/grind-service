package com.grindandtrain.worker.keyring;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.keyring.service.KeyringService;
import com.grindandtrain.contract.event.EventType;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.worker.common.messaging.EventHandler;
import com.grindandtrain.worker.common.messaging.HandlerOrder;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Removes the user's keyring when their account is deleted.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@Order(HandlerOrder.DATA_CLEANUP)
public class KeyringAccountDeletionHandler implements EventHandler<AccountDeletionRequestedEvent> {

    private final KeyringService keyringService;

    public KeyringAccountDeletionHandler(KeyringService keyringService) {
        this.keyringService = keyringService;
    }

    @Override
    public EventType eventType() {
        return EventType.ACCOUNT_DELETION_REQUESTED;
    }

    @Override
    public void handle(AccountDeletionRequestedEvent event) {
        keyringService.deleteForUser(UserId.of(event.userId()));
    }
}
