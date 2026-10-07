package com.grindandtrain.worker.sync;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.sync.service.RecordService;
import com.grindandtrain.contract.event.EventType;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.worker.common.messaging.EventHandler;
import com.grindandtrain.worker.common.messaging.HandlerOrder;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Removes all of a user's blobs and records when their account is deleted.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@Order(HandlerOrder.DATA_CLEANUP)
public class SyncAccountDeletionHandler implements EventHandler<AccountDeletionRequestedEvent> {

    private final RecordService recordService;

    public SyncAccountDeletionHandler(RecordService recordService) {
        this.recordService = recordService;
    }

    @Override
    public EventType eventType() {
        return EventType.ACCOUNT_DELETION_REQUESTED;
    }

    @Override
    public void handle(AccountDeletionRequestedEvent event) {
        recordService.deleteAllForUser(UserId.of(event.userId()));
    }
}
