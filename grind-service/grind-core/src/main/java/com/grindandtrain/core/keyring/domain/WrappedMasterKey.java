package com.grindandtrain.core.keyring.domain;

/**
 * The user's master key, wrapped on the phone with a key derived from their passphrase and,
 * separately, with their recovery key.
 *
 * <p>The server can't unwrap it. {@code checkValue} lets a new device confirm the passphrase before
 * downloading anything. Binary values are base64 encoded.
 *
 * @author Dheeraj_Edupuganti
 */
public record WrappedMasterKey(
        int formatVersion,
        String kdfSalt,
        int kdfIterations,
        String wrappedByPassphrase,
        String wrappedByRecoveryKey,
        String checkValue) {
}
