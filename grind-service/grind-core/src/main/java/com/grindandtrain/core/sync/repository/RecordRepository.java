package com.grindandtrain.core.sync.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.grindandtrain.common.domain.RecordId;
import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.sync.domain.BlobReference;
import com.grindandtrain.core.sync.domain.EncryptedRecord;
import com.grindandtrain.core.sync.domain.RecordChange;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@code records}.
 *
 * <p>Use inside {@link com.grindandtrain.core.common.persistence.UserTransactionTemplate}. Row-level
 * security already limits every query to that user; the explicit {@code user_id} conditions are
 * there for the index and as a second safeguard.
 *
 * @author Dheeraj_Edupuganti
 */
@Repository
public class RecordRepository {

    /** The stored revision number, blob key and size in bytes (inline or blob) of one record. */
    public record StoredVersion(long revision, String blobKey, long storedBytes) {
    }

    private static final String STORED_VERSION_COLUMNS =
            "rev, blob_key, coalesce(blob_size, octet_length(inline_ct), 0) as stored_bytes";

    private final JdbcClient jdbcClient;

    public RecordRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<StoredVersion> findVersion(UserId userId, RecordId recordId) {
        return jdbcClient.sql("select " + STORED_VERSION_COLUMNS + " from records where user_id = :userId and id = :recordId")
                .param("userId", userId.value()).param("recordId", recordId.value())
                .query((rs, rowNum) -> toStoredVersion(rs))
                .optional();
    }

    /** Same as {@link #findVersion}, locking the row until the transaction ends. */
    public Optional<StoredVersion> findVersionForUpdate(UserId userId, RecordId recordId) {
        return jdbcClient.sql("select " + STORED_VERSION_COLUMNS
                        + " from records where user_id = :userId and id = :recordId for update")
                .param("userId", userId.value()).param("recordId", recordId.value())
                .query((rs, rowNum) -> toStoredVersion(rs))
                .optional();
    }

    /** Inserts the record, or replaces an older revision of it. A newer stored revision is left untouched. */
    public void upsert(UserId userId, EncryptedRecord record) {
        BlobReference blob = record.blob();
        jdbcClient.sql("""
                insert into records (user_id, id, rev, seq, deleted, updated_at,
                                     inline_ct, blob_key, blob_size, blob_sha256, wrapped_dek)
                values (:userId, :recordId, :revision, nextval('record_seq'), :deleted, now(),
                        :inlineCiphertext, :blobKey, :blobSize, :blobSha256, :wrappedKey)
                on conflict (user_id, id) do update set
                    rev = excluded.rev,
                    seq = excluded.seq,
                    deleted = excluded.deleted,
                    updated_at = excluded.updated_at,
                    inline_ct = excluded.inline_ct,
                    blob_key = excluded.blob_key,
                    blob_size = excluded.blob_size,
                    blob_sha256 = excluded.blob_sha256,
                    wrapped_dek = excluded.wrapped_dek
                where records.rev < excluded.rev""")
                .param("userId", userId.value())
                .param("recordId", record.id().value())
                .param("revision", record.revision())
                .param("deleted", record.deleted())
                .param("inlineCiphertext", record.inlineCiphertext())
                .param("blobKey", blob == null ? null : blob.key())
                .param("blobSize", blob == null ? null : blob.size())
                .param("blobSha256", blob == null ? null : blob.sha256())
                .param("wrappedKey", record.wrappedKey())
                .update();
    }

    /** Changes after {@code afterSequence}, oldest first. */
    public List<RecordChange> findChangesAfter(UserId userId, long afterSequence, int limit) {
        return jdbcClient.sql("""
                select id, rev, seq, deleted, updated_at, inline_ct, blob_key, blob_size, blob_sha256, wrapped_dek
                from records
                where user_id = :userId and seq > :afterSequence
                order by seq
                limit :limit""")
                .param("userId", userId.value()).param("afterSequence", afterSequence).param("limit", limit)
                .query((rs, rowNum) -> toChange(rs))
                .list();
    }

    public Optional<String> findLiveBlobKey(UserId userId, RecordId recordId) {
        return jdbcClient.sql("""
                select blob_key from records
                where user_id = :userId and id = :recordId and not deleted and blob_key is not null""")
                .param("userId", userId.value()).param("recordId", recordId.value())
                .query(String.class)
                .optional();
    }

    /** Every blob key the user's records point to. */
    public Set<String> findBlobKeys(UserId userId) {
        return Set.copyOf(jdbcClient.sql("select blob_key from records where user_id = :userId and blob_key is not null")
                .param("userId", userId.value())
                .query(String.class)
                .list());
    }

    /** Bytes the user's records occupy, computed from the records themselves. */
    public long sumStoredBytes(UserId userId) {
        return jdbcClient.sql("""
                select coalesce(sum(coalesce(blob_size, octet_length(inline_ct), 0)), 0)
                from records
                where user_id = :userId""")
                .param("userId", userId.value())
                .query(Long.class)
                .single();
    }

    public void deleteAllByUser(UserId userId) {
        jdbcClient.sql("delete from records where user_id = :userId").param("userId", userId.value()).update();
    }

    private static StoredVersion toStoredVersion(ResultSet rs) throws SQLException {
        return new StoredVersion(rs.getLong("rev"), rs.getString("blob_key"), rs.getLong("stored_bytes"));
    }

    private static RecordChange toChange(ResultSet rs) throws SQLException {
        String blobKey = rs.getString("blob_key");
        BlobReference blob = blobKey == null ? null
                : new BlobReference(blobKey, rs.getLong("blob_size"), rs.getString("blob_sha256"));
        EncryptedRecord record = new EncryptedRecord(RecordId.of(rs.getObject("id", UUID.class)), rs.getLong("rev"),
                rs.getBoolean("deleted"), rs.getBytes("inline_ct"), blob, rs.getString("wrapped_dek"));
        return new RecordChange(record, rs.getLong("seq"), rs.getObject("updated_at", OffsetDateTime.class));
    }
}
