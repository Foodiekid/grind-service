package com.grindandtrain.core.upload.service;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.common.config.CoreProperties;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.common.storage.BlobStorage;
import com.grindandtrain.core.upload.domain.UploadGrant;

import org.springframework.stereotype.Service;

/**
 * Issues short-lived upload URLs, so encrypted blobs go straight to R2 instead of through the API.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class UploadService {

    private final BlobStorage blobStorage;
    private final AccountService accountService;
    private final long maxBlobBytes;

    public UploadService(BlobStorage blobStorage, AccountService accountService, CoreProperties properties) {
        this.blobStorage = blobStorage;
        this.accountService = accountService;
        this.maxBlobBytes = properties.limits().maxBlobBytes();
    }

    public UploadGrant createUpload(UserId userId, long sizeBytes, String sha256) {
        accountService.ensureActive(userId);
        if (sizeBytes > maxBlobBytes) {
            throw new GrindException(BusinessErrorCode.BLOB_TOO_LARGE);
        }
        BlobStorage.PresignedUpload upload = blobStorage.presignUpload(userId, sizeBytes, sha256);
        return new UploadGrant(upload.key(), upload.url(), upload.headers(), upload.expiresAt());
    }
}
