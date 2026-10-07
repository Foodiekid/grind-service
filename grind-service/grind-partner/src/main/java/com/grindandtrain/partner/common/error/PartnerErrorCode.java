package com.grindandtrain.partner.common.error;

import com.grindandtrain.common.error.ErrorCategory;
import com.grindandtrain.common.error.ErrorCode;

/**
 * Errors of the partner service. Thrown as {@link com.grindandtrain.common.error.GrindException}; the shared exception
 * handler turns the category into an HTTP status. Codes are part of the public contract: add new ones, but don't
 * rename existing ones.
 *
 * @author Dheeraj_Edupuganti
 */
public enum PartnerErrorCode implements ErrorCode {

    INVALID_CONNECTION_REQUEST(ErrorCategory.INVALID_REQUEST, "invalid_connection_request", "Missing code, redirect URI or refresh token"),
    REDIRECT_URI_NOT_ALLOWED(ErrorCategory.INVALID_REQUEST, "redirect_uri_not_allowed", "Redirect URI not registered"),
    CONNECTION_NOT_CONFIGURED(ErrorCategory.NOT_FOUND, "connection_not_configured", "This provider isn't available"),
    CONNECTION_REJECTED(ErrorCategory.UNPROCESSABLE, "connection_rejected", "The provider refused; connect again"),
    PROVIDER_UNAVAILABLE(ErrorCategory.UNAVAILABLE, "provider_unavailable", "The provider didn't answer; try again shortly");

    private final ErrorCategory category;
    private final String code;
    private final String title;

    PartnerErrorCode(ErrorCategory category, String code, String title) {
        this.category = category;
        this.code = code;
        this.title = title;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String title() {
        return title;
    }
}
