package com.grindandtrain.common.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * The id of a signed-in user: the subject of their Supabase access token.
 * <p>
 * Users are never logged by their real id. {@link #pseudonym()} gives a short, stable hash: support can compute it
 * from a user's id to find their requests, but a log reader can't go the other way. {@link #toString()} returns the
 * pseudonym too, so a user id that ends up in a log line by accident still doesn't reveal who it was. Use
 * {@link #value()} where the real id is needed (database parameters, storage keys, events).
 *
 * @author Dheeraj_Edupuganti
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    /** Parses a token subject. Throws {@link IllegalArgumentException} when it is not a UUID. */
    public static UserId parse(String value) {
        return new UserId(UUID.fromString(value));
    }

    /** The first 16 hex characters of the SHA-256 of the id. */
    public String pseudonym() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    @Override
    public String toString() {
        return "user:" + pseudonym();
    }
}
