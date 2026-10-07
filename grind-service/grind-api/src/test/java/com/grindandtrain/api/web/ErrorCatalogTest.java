package com.grindandtrain.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.error.PlatformErrorCode;
import com.grindandtrain.common.testkit.ErrorCatalog;
import com.grindandtrain.core.common.error.BusinessErrorCode;

import org.junit.jupiter.api.Test;

/**
 * Every problem code grind-api can return is in the app's error catalog, with the status the server really sends, so
 * the app always has a specific message and action for it.
 *
 * @author Dheeraj_Edupuganti
 */
class ErrorCatalogTest {

    @Test
    void catalogDescribesEveryCodeTheApiReturns() {
        List<ErrorCode> codes = new ArrayList<>(List.of(PlatformErrorCode.values()));
        codes.addAll(List.of(BusinessErrorCode.values()));

        ErrorCatalog.assertDescribes(Set.of("platform", "core"), codes);
    }
}
