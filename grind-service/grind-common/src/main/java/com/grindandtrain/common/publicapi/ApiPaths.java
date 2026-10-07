package com.grindandtrain.common.publicapi;

import java.util.regex.Pattern;

/**
 * URL layout of the public API: {@code /grind/api/v<n>/...}. Health probes live outside it (see
 * {@link com.grindandtrain.common.web.ProbePaths}).
 *
 * @author Dheeraj_Edupuganti
 */
public final class ApiPaths {

    public static final String BASE = "/grind/api";

    /** Ant-style pattern matching every version, for Spring Security and CORS. */
    public static final String ALL_VERSIONS = BASE + "/v*/**";

    /** Matches any versioned API path; group 1 is the version, e.g. {@code v1}. */
    public static final Pattern VERSIONED_PATH = Pattern.compile("^" + BASE + "/(v\\d+)/.*");

    private ApiPaths() {
    }

    public static boolean isApiPath(String requestUri) {
        return VERSIONED_PATH.matcher(requestUri).matches();
    }
}
