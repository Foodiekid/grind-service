package com.grindandtrain.api.keyring.web.v1;

import com.grindandtrain.common.publicapi.CurrentUser;
import com.grindandtrain.common.error.GrindException;
import com.grindandtrain.contract.api.v1.KeyringApi;
import com.grindandtrain.contract.api.v1.dto.Keyring;
import com.grindandtrain.contract.api.v1.dto.KeyringWrite;
import com.grindandtrain.core.common.error.BusinessErrorCode;
import com.grindandtrain.core.keyring.service.KeyringService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keyring endpoints of API v1.
 *
 * @author Dheeraj_Edupuganti
 */
@RestController
public class KeyringController implements KeyringApi {

    private final KeyringService keyringService;

    public KeyringController(KeyringService keyringService) {
        this.keyringService = keyringService;
    }

    @Override
    public ResponseEntity<Keyring> getKeyring() {
        return keyringService.find(CurrentUser.id())
                .map(KeyringMapper::toKeyring)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new GrindException(BusinessErrorCode.NOT_FOUND));
    }

    @Override
    public ResponseEntity<Keyring> putKeyring(KeyringWrite keyringWrite) {
        var saved = keyringService.save(CurrentUser.id(), keyringWrite.getExpectedRev(),
                KeyringMapper.toWrappedMasterKey(keyringWrite));
        return ResponseEntity.ok(KeyringMapper.toKeyring(saved));
    }
}
