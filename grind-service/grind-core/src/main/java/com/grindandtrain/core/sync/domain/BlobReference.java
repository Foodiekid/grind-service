package com.grindandtrain.core.sync.domain;

/**
 * Points to a record's encrypted payload in R2, with its exact size and SHA-256 (base64).
 *
 * @author Dheeraj_Edupuganti
 */
public record BlobReference(String key, long size, String sha256) {
}
