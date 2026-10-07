package com.grindandtrain.partner.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.testkit.ErrorCatalog;
import com.grindandtrain.partner.common.error.PartnerErrorCode;

import org.junit.jupiter.api.Test;

/**
 * Every problem code grind-partner can return is in the app's error catalog, with the status the server really sends.
 *
 * @author Dheeraj_Edupuganti
 */
class ErrorCatalogTest {

    @Test
    void catalogDescribesEveryCodeThePartnerServiceReturns() {
        List<ErrorCode> codes = new ArrayList<>(List.of(PlatformErrorCode.values()));
        codes.addAll(List.of(PartnerErrorCode.values()));

        ErrorCatalog.assertDescribes(Set.of("platform", "partner"), codes);
    }
}
