package com.grindandtrain.partner.connection.domain;

import java.util.Objects;
import java.util.regex.Pattern;

import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.partner.common.error.PartnerErrorCode;

/**
 * Which partner a connection is with, by the name used in the API path and the settings ({@code oura}, {@code whoop},
 * later {@code google-health}). Partners are configuration, not code: a partner exists when
 * {@code grind.partners.providers.<id>} is set.
 *
 * @author Dheeraj_Edupuganti
 */
public record PartnerId(String value) {

    private static final Pattern VALID = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    public PartnerId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new GrindException(PartnerErrorCode.CONNECTION_NOT_CONFIGURED);
        }
    }

    public static PartnerId of(String value) {
        return new PartnerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
