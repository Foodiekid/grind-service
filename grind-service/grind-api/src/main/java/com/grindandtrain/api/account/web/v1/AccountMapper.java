package com.grindandtrain.api.account.web.v1;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.contract.api.v1.dto.Me;

/**
 * Converts account data to the v1 response types.
 *
 * @author Dheeraj_Edupuganti
 */
final class AccountMapper {

    private AccountMapper() {
    }

    static Me toMe(UserId userId) {
        return new Me(userId.value());
    }
}
