package com.grindandtrain.api.sync.web.v1;

import java.util.UUID;

import com.grindandtrain.common.publicapi.CurrentUser;
import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.contract.api.v1.RecordsApi;
import com.grindandtrain.contract.api.v1.dto.DownloadTicket;
import com.grindandtrain.contract.api.v1.dto.RecordAccepted;
import com.grindandtrain.contract.api.v1.dto.RecordPage;
import com.grindandtrain.contract.api.v1.dto.RecordWrite;
import com.grindandtrain.core.sync.service.RecordService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Record sync endpoints of API v1.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class RecordController implements RecordsApi {

    private static final int DEFAULT_PAGE_SIZE = 200;

    private final RecordService recordService;

    public RecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @Override
    public ResponseEntity<RecordAccepted> putRecord(UUID id, RecordWrite recordWrite) {
        recordService.submit(CurrentUser.id(), RecordMapper.toEncryptedRecord(RecordId.of(id), recordWrite));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RecordAccepted(id, recordWrite.getRev()));
    }

    @Override
    public ResponseEntity<RecordPage> listRecords(String cursor, Integer limit) {
        int pageSize = limit == null ? DEFAULT_PAGE_SIZE : limit;
        return ResponseEntity.ok(RecordMapper.toRecordPage(recordService.findChanges(CurrentUser.id(), cursor, pageSize)));
    }

    @Override
    public ResponseEntity<DownloadTicket> getRecordBlob(UUID id) {
        return ResponseEntity.ok(RecordMapper.toDownloadTicket(recordService.createBlobDownload(CurrentUser.id(), RecordId.of(id))));
    }
}
