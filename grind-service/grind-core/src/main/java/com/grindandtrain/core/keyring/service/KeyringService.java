package com.grindandtrain.core.keyring.service;

import java.util.Optional;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.core.account.service.AccountService;
import com.grindandtrain.core.common.logging.AuditEvent;
import com.grindandtrain.common.logging.AuditLogger;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;
import com.grindandtrain.core.keyring.domain.StoredKeyring;
import com.grindandtrain.core.keyring.domain.WrappedMasterKey;
import com.grindandtrain.core.keyring.repository.KeyringRepository;

import org.springframework.stereotype.Service;

/**
 * Reads and saves the user's wrapped master key.
 *
 * <p>Saves are compare-and-swap on the revision, so two devices setting up sync at the same time
 * can't overwrite each other's keyring without noticing.
 *
 * @author Dheeraj_Edupuganti
 */
@Service
public class KeyringService {

    private final UserTransactionTemplate userTransactions;
    private final KeyringRepository keyringRepository;
    private final AccountService accountService;
    private final AuditLogger auditLogger;

    public KeyringService(UserTransactionTemplate userTransactions, KeyringRepository keyringRepository,
            AccountService accountService, AuditLogger auditLogger) {
        this.userTransactions = userTransactions;
        this.keyringRepository = keyringRepository;
        this.accountService = accountService;
        this.auditLogger = auditLogger;
    }

    public Optional<StoredKeyring> find(UserId userId) {
        return userTransactions.execute(userId, () -> keyringRepository.findByUser(userId));
    }

    /** Creates ({@code expectedRevision} 0) or replaces the keyring; 409 if it changed since the caller read it. */
    public StoredKeyring save(UserId userId, long expectedRevision, WrappedMasterKey key) {
        accountService.ensureActive(userId);
        StoredKeyring stored = userTransactions.execute(userId, () -> {
            boolean saved = expectedRevision == 0
                    ? keyringRepository.insert(userId, key)
                    : keyringRepository.updateIfRevisionMatches(userId, expectedRevision, key);
            if (!saved) {
                throw new GrindException(BusinessErrorCode.KEYRING_CHANGED);
            }
            return keyringRepository.findByUser(userId).orElseThrow();
        });
        auditLogger.record(expectedRevision == 0 ? AuditEvent.KEYRING_CREATED : AuditEvent.KEYRING_REPLACED, userId);
        return stored;
    }

    public void deleteForUser(UserId userId) {
        userTransactions.executeWithoutResult(userId, () -> keyringRepository.deleteByUser(userId));
    }
}
