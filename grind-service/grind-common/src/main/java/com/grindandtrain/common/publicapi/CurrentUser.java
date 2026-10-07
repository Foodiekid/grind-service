package com.grindandtrain.common.publicapi;

import java.util.Optional;

import com.grindandtrain.common.domain.UserId;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The signed-in user of the current request, taken from the verified Supabase token.
 *
 * @author Dheeraj_Edupuganti
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** For controllers: the user, or 401 when the token's subject is not a user id. */
    public static UserId id() {
        return find().orElseThrow(() -> new BadCredentialsException("Token subject is not a user id"));
    }

    /** Empty when nobody is signed in or the token's subject is not a UUID. */
    public static Optional<UserId> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.getToken().getSubject() != null) {
            try {
                return Optional.of(UserId.parse(token.getToken().getSubject()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
