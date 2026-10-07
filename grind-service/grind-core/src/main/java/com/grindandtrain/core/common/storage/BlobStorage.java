package com.grindandtrain.core.common.storage;

import java.net.URI;
import java.time.Instant;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.common.config.CoreProperties;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * Stores encrypted blobs in Cloudflare R2.
 *
 * <p>Every key has the form {@code u/<userId>/<random uuid>}: all of a user's blobs share one prefix
 * (used when deleting an account) and a key belonging to someone else is easy to spot. The server
 * never reads blob contents.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class BlobStorage {

    /** A signed PUT: the phone sends {@code headers} unchanged with the body. */
    public record PresignedUpload(String key, URI url, Map<String, String> headers, OffsetDateTime expiresAt) {
    }

    public record PresignedDownload(URI url, OffsetDateTime expiresAt) {
    }

    /** One stored blob: its key and when it was uploaded. */
    public record StoredBlob(String key, Instant uploadedAt) {
    }

    private static final String USERS_PREFIX = "u/";

    private final S3Client s3;
    private final S3Presigner presigner;
    private final CoreProperties.Storage r2;
    private final Clock clock;

    public BlobStorage(S3Client s3, S3Presigner presigner, CoreProperties properties, Clock clock) {
        this.s3 = s3;
        this.presigner = presigner;
        this.r2 = properties.storage();
        this.clock = clock;
    }

    public static String userPrefix(UserId userId) {
        return USERS_PREFIX + userId.value() + "/";
    }

    /** Every user who has blobs, read lazily page by page (one folder per user under {@code u/}). */
    public Stream<UserId> usersWithBlobs() {
        ListObjectsV2Request list = ListObjectsV2Request.builder()
                .bucket(r2.bucket()).prefix(USERS_PREFIX).delimiter("/").build();
        return s3.listObjectsV2Paginator(list).commonPrefixes().stream()
                .map(prefix -> prefix.prefix().substring(USERS_PREFIX.length(), prefix.prefix().length() - 1))
                .flatMap(id -> {
                    try {
                        return Stream.of(UserId.of(UUID.fromString(id)));
                    } catch (IllegalArgumentException e) {
                        return Stream.empty(); // not a user folder; never ours to touch
                    }
                });
    }

    /** All of one user's blobs. */
    public List<StoredBlob> listBlobs(UserId userId) {
        ListObjectsV2Request list = ListObjectsV2Request.builder().bucket(r2.bucket()).prefix(userPrefix(userId)).build();
        List<StoredBlob> blobs = new ArrayList<>();
        for (S3Object object : s3.listObjectsV2Paginator(list).contents()) {
            blobs.add(new StoredBlob(object.key(), object.lastModified()));
        }
        return blobs;
    }

    /** A PUT URL bound to this exact size and SHA-256 (base64): storage rejects any other body. */
    public PresignedUpload presignUpload(UserId userId, long size, String sha256) {
        String key = userPrefix(userId) + UUID.randomUUID();
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(r2.bucket())
                .key(key)
                .contentLength(size)
                .checksumSHA256(sha256)
                .contentType("application/octet-stream")
                .build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(p -> p.signatureDuration(r2.urlTtl()).putObjectRequest(put));
        Map<String, String> headers = new HashMap<>();
        // Everything signed except Host, which the phone's HTTP client sets itself.
        signed.signedHeaders().forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name)) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUpload(key, URI.create(signed.url().toString()), Map.copyOf(headers), expiry());
    }

    public PresignedDownload presignDownload(String key) {
        var signed = presigner.presignGetObject(p -> p.signatureDuration(r2.urlTtl())
                .getObjectRequest(g -> g.bucket(r2.bucket()).key(key)));
        return new PresignedDownload(URI.create(signed.url().toString()), expiry());
    }

    /** The stored size, or empty when there's no such blob. */
    public Optional<Long> size(String key) {
        try {
            return Optional.of(s3.headObject(h -> h.bucket(r2.bucket()).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    public void delete(String key) {
        s3.deleteObject(d -> d.bucket(r2.bucket()).key(key));
    }

    /** Deletes everything under a prefix, 1,000 keys per request. Safe to repeat. */
    public void deleteByPrefix(String prefix) {
        ListObjectsV2Request list = ListObjectsV2Request.builder().bucket(r2.bucket()).prefix(prefix).build();
        List<ObjectIdentifier> batch = new ArrayList<>();
        for (S3Object object : s3.listObjectsV2Paginator(list).contents()) {
            batch.add(ObjectIdentifier.builder().key(object.key()).build());
            if (batch.size() == 1000) {
                deleteBatch(batch);
            }
        }
        if (!batch.isEmpty()) {
            deleteBatch(batch);
        }
    }

    private void deleteBatch(List<ObjectIdentifier> batch) {
        s3.deleteObjects(d -> d.bucket(r2.bucket()).delete(Delete.builder().objects(List.copyOf(batch)).quiet(true).build()));
        batch.clear();
    }

    private OffsetDateTime expiry() {
        return OffsetDateTime.now(clock).plus(r2.urlTtl());
    }
}
