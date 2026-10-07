package com.grindandtrain.common.http;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Request factories for outbound HTTP calls (Supabase, partners). Every outbound call in GRIND has a connect and a
 * request timeout, so a slow third party can't hold threads; use this instead of a client without limits.
 *
 * @author Dheeraj_Edupuganti
 */
public final class HttpRequestFactories {

    private HttpRequestFactories() {
    }

    /** One factory owns one connection pool: build it once per destination and reuse it. */
    public static ClientHttpRequestFactory withTimeouts(Duration connectTimeout, Duration requestTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requestTimeout);
        return requestFactory;
    }
}
