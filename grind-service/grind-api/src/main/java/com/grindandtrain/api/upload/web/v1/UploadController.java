package com.grindandtrain.api.upload.web.v1;

import com.grindandtrain.common.publicapi.CurrentUser;
import com.grindandtrain.contract.api.v1.UploadsApi;
import com.grindandtrain.contract.api.v1.dto.UploadRequest;
import com.grindandtrain.contract.api.v1.dto.UploadTicket;
import com.grindandtrain.core.upload.service.UploadService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload endpoints of API v1.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class UploadController implements UploadsApi {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Override
    public ResponseEntity<UploadTicket> createUpload(UploadRequest uploadRequest) {
        var upload = uploadService.createUpload(CurrentUser.id(), uploadRequest.getSize(), uploadRequest.getSha256());
        return ResponseEntity.ok(UploadMapper.toUploadTicket(upload));
    }
}
