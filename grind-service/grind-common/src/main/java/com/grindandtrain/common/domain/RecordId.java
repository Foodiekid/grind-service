package com.grindandtrain.common.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The id of one synced record, chosen by the phone when the record is created. A random UUID that says nothing about
 * what the record contains.
 *
 * @author Dheeraj_Edupuganti
 */
public record RecordId(UUID value) {

    public RecordId {
        Objects.requireNonNull(value, "value");
    }

    public static RecordId of(UUID value) {
        return new RecordId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
