package com.grindandtrain.core.upload.domain;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Permission to upload one encrypted blob straight to storage: where to send it, the headers that must go with it
 * (they are part of the signature), and when the permission runs out.
 *
 * @author Dheeraj_Edupuganti
 */
public record UploadGrant(String blobKey, URI url, Map<String, String> headers, OffsetDateTime expiresAt) {

    public UploadGrant {
        headers = Map.copyOf(headers);
    }
}
