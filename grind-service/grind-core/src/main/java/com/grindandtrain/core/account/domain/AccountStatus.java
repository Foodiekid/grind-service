package com.grindandtrain.core.account.domain;

import java.util.Locale;

/**
 * Where an account is in its life, as stored in {@code accounts.status}.
 *
 * <p>Only an active account may change data. A deleting account is having its data removed; a deleted one keeps just
 * its row, so a late write from another device is refused.
 *
 * @author Dheeraj_Edupuganti
 */
public enum AccountStatus {
    ACTIVE,
    DELETING,
    DELETED;

    public static AccountStatus fromDatabase(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
