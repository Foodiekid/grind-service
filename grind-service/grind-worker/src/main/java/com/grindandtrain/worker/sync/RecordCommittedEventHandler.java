package com.grindandtrain.worker.sync;

import com.grindandtrain.core.sync.service.RecordService;
import com.grindandtrain.contract.event.EventType;
import com.grindandtrain.contract.event.sync.RecordCommittedEvent;
import com.grindandtrain.worker.common.messaging.EventHandler;

import org.springframework.stereotype.Component;

/**
 * Stores record revisions accepted by the API.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class RecordCommittedEventHandler implements EventHandler<RecordCommittedEvent> {

    private final RecordService recordService;

    public RecordCommittedEventHandler(RecordService recordService) {
        this.recordService = recordService;
    }

    @Override
    public EventType eventType() {
        return EventType.RECORD_COMMITTED;
    }

    @Override
    public void handle(RecordCommittedEvent event) {
        recordService.store(event);
    }
}
