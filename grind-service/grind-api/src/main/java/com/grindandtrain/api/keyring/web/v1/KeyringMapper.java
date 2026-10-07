package com.grindandtrain.api.keyring.web.v1;

import com.grindandtrain.contract.api.v1.dto.Keyring;
import com.grindandtrain.contract.api.v1.dto.KeyringWrite;
import com.grindandtrain.core.keyring.domain.StoredKeyring;
import com.grindandtrain.core.keyring.domain.WrappedMasterKey;

/**
 * Converts between the v1 keyring request/response types and the keyring domain.
 *
 * @author Dheeraj_Edupuganti
 */
final class KeyringMapper {

    private KeyringMapper() {
    }

    static WrappedMasterKey toWrappedMasterKey(KeyringWrite request) {
        return new WrappedMasterKey(
                request.getVersion(),
                request.getSalt(),
                request.getIterations(),
                request.getWrappedByPassphrase(),
                request.getWrappedByRecovery(),
                request.getCheck());
    }

    static Keyring toKeyring(StoredKeyring stored) {
        WrappedMasterKey key = stored.masterKey();
        return new Keyring(
                key.formatVersion(),
                key.kdfSalt(),
                key.kdfIterations(),
                key.wrappedByPassphrase(),
                key.wrappedByRecoveryKey(),
                key.checkValue(),
                stored.revision());
    }
}
