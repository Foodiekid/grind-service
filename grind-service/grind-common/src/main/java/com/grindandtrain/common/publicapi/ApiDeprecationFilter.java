package com.grindandtrain.common.publicapi;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.grindandtrain.common.web.FilterOrder;

import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@code Deprecation} (RFC 9745) and {@code Sunset} (RFC 8594) headers to responses of an
 * API version that is being retired.
 *
 * <p>Versions are configured under {@code grind.api.retiring-versions}; nothing is added for versions that
 * aren't listed.
 *
 * @author Dheeraj_Edupuganti
 */
@Order(FilterOrder.SERVICE + 3)
public class ApiDeprecationFilter extends OncePerRequestFilter {

    private final Map<String, PublicApiProperties.Retirement> retiring;

    public ApiDeprecationFilter(PublicApiProperties properties) {
        this.retiring = properties.retiringVersions();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return retiring.isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher m = ApiPaths.VERSIONED_PATH.matcher(request.getRequestURI());
        if (m.matches()) {
            PublicApiProperties.Retirement retirement = retiring.get(m.group(1));
            if (retirement != null) {
                response.setHeader(ApiHeaders.DEPRECATION, "@" + retirement.deprecatedOn().atStartOfDay(ZoneOffset.UTC).toEpochSecond());
                response.setHeader(ApiHeaders.SUNSET, DateTimeFormatter.RFC_1123_DATE_TIME.format(retirement.sunsetOn().atStartOfDay(ZoneOffset.UTC)));
            }
        }
        chain.doFilter(request, response);
    }
}
