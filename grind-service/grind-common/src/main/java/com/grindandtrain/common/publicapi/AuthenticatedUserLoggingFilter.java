package com.grindandtrain.common.publicapi;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.logging.LogFields;
import com.grindandtrain.common.logging.MdcScope;
import com.grindandtrain.common.web.RequestLoggingFilter;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the signed-in user's pseudonym on every log line once the token has been verified, and on the access-log line.
 * Registered inside the security filter chain, right after bearer-token authentication (see PublicApiConfiguration), so it
 * is not a bean: Spring Boot would otherwise also run it as a plain servlet filter, before authentication.
 *
 * @author Dheeraj_Edupuganti
 */
public class AuthenticatedUserLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> user = CurrentUser.find().map(UserId::pseudonym);
        user.ifPresent(pseudonym -> RequestLoggingFilter.remember(request, LogFields.USER, pseudonym));
        try (MdcScope scope = MdcScope.open().put(LogFields.USER, user.orElse(null))) {
            chain.doFilter(request, response);
        }
    }
}
