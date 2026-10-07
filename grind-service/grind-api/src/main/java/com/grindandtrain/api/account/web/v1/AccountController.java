package com.grindandtrain.api.account.web.v1;

import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.common.publicapi.CurrentUser;
import com.grindandtrain.contract.api.v1.AccountApi;
import com.grindandtrain.contract.api.v1.dto.Me;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account endpoints of API v1.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class AccountController implements AccountApi {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ResponseEntity<Me> getMe() {
        return ResponseEntity.ok(AccountMapper.toMe(CurrentUser.id()));
    }

    @Override
    public ResponseEntity<Void> deleteAccount() {
        accountService.requestDeletion(CurrentUser.id());
        return ResponseEntity.accepted().build();
    }
}
