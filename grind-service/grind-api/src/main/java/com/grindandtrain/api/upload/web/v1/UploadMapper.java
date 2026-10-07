package com.grindandtrain.api.upload.web.v1;

import com.grindandtrain.contract.api.v1.dto.UploadTicket;
import com.grindandtrain.core.upload.domain.UploadGrant;

/**
 * Converts an upload grant to the v1 response type.
 *
 * @author Dheeraj_Edupuganti
 */
final class UploadMapper {

    private UploadMapper() {
    }

    static UploadTicket toUploadTicket(UploadGrant grant) {
        return new UploadTicket(grant.blobKey(), grant.url(), grant.headers(), grant.expiresAt());
    }
}
