package com.grindandtrain.worker.account;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.contract.event.EventType;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;
import com.grindandtrain.worker.common.messaging.EventHandler;
import com.grindandtrain.worker.common.messaging.HandlerOrder;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Last step of an account deletion, after every module has removed its data: deletes the Supabase user and marks
 * the deletion complete.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
@Order(HandlerOrder.COMPLETION)
public class AccountDeletionCompletionHandler implements EventHandler<AccountDeletionRequestedEvent> {

    private final SupabaseAdminClient supabaseAdminClient;
    private final AccountService accountService;

    public AccountDeletionCompletionHandler(SupabaseAdminClient supabaseAdminClient, AccountService accountService) {
        this.supabaseAdminClient = supabaseAdminClient;
        this.accountService = accountService;
    }

    @Override
    public EventType eventType() {
        return EventType.ACCOUNT_DELETION_REQUESTED;
    }

    @Override
    public void handle(AccountDeletionRequestedEvent event) {
        supabaseAdminClient.deleteUser(UserId.of(event.userId()));
        accountService.markDeletionCompleted(UserId.of(event.userId()));
    }
}
