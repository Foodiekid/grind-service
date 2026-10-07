package com.grindandtrain.core.keyring.domain;

/**
 * A user's keyring as stored, with the revision used for compare-and-swap updates.
 *
 * @author Dheeraj_Edupuganti
 */
public record StoredKeyring(long revision, WrappedMasterKey masterKey) {
}
