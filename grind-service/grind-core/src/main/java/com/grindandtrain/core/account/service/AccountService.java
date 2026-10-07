package com.grindandtrain.core.account.service;

import java.time.Instant;
import java.util.List;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.core.account.domain.AccountStatus;
import com.grindandtrain.core.account.repository.AccountRepository;
import com.grindandtrain.core.common.logging.AuditEvent;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.core.common.messaging.EventPublisher;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.contract.event.account.AccountDeletionRequestedEvent;

import org.springframework.stereotype.Service;

/**
 * Account lifecycle. Other modules check here whether an account may still change data; the first such check
 * creates the account row that all of the user's data belongs to.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class AccountService {

    private final UserTransactionTemplate userTransactions;
    private final AccountRepository accountRepository;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;

    public AccountService(UserTransactionTemplate userTransactions, AccountRepository accountRepository,
            EventPublisher eventPublisher, AuditLogger auditLogger) {
        this.userTransactions = userTransactions;
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
    }

    public boolean isDeletionRequested(UserId userId) {
        return userTransactions.execute(userId, () -> accountRepository.findStatus(userId))
                .filter(status -> status != AccountStatus.ACTIVE)
                .isPresent();
    }

    /**
     * Creates the account on the user's first write, so their data has its parent row, and refuses writes (410 Gone)
     * once the account's deletion has been requested.
     */
    public void ensureActive(UserId userId) {
        AccountStatus status = userTransactions.execute(userId,
                () -> accountRepository.createIfAbsentAndGetStatus(userId));
        if (status != AccountStatus.ACTIVE) {
            throw new GrindException(BusinessErrorCode.ACCOUNT_DELETED);
        }
    }

    /** Records the request at once, then queues the clean-up. Calling it again resumes an interrupted deletion. */
    public void requestDeletion(UserId userId) {
        userTransactions.executeWithoutResult(userId, () -> accountRepository.markDeleting(userId));
        eventPublisher.publish(new AccountDeletionRequestedEvent(userId.value()));
        auditLogger.record(AuditEvent.ACCOUNT_DELETION_REQUESTED, userId);
    }

    /** Deletions requested before {@code requestedBefore} that never completed, oldest first (ids only). */
    public List<UserId> findStuckDeletions(Instant requestedBefore, int maxRows) {
        return accountRepository.findStuck(requestedBefore, maxRows);
    }

    /** Publishes the deletion again, for one that got stuck. The worker's handlers are idempotent, so this is safe. */
    public void resumeDeletion(UserId userId) {
        eventPublisher.publish(new AccountDeletionRequestedEvent(userId.value()));
        auditLogger.record(AuditEvent.ACCOUNT_DELETION_RESUMED, userId);
    }

    public void markDeletionCompleted(UserId userId) {
        userTransactions.executeWithoutResult(userId, () -> accountRepository.markDeleted(userId));
        auditLogger.record(AuditEvent.ACCOUNT_DELETION_COMPLETED, userId);
    }
}
