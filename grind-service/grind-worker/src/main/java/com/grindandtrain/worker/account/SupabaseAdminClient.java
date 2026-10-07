package com.grindandtrain.worker.account;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.common.http.HttpRequestFactories;
import com.grindandtrain.worker.common.config.WorkerProperties;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for the Supabase Auth admin API.
 * <p>
 * It holds the service-role key, so it is used for one thing only: deleting the sign-in user at the end of an account
 * deletion. Every call has a connect and a request timeout, so a slow Supabase can't hold worker threads; a timed-out
 * call fails the event and Pub/Sub retries it later.
 *
 * @author Dheeraj_Edupuganti
 */
@Component
public class SupabaseAdminClient {

    private final RestClient restClient;

    public SupabaseAdminClient(RestClient.Builder restClientBuilder, WorkerProperties properties) {
        WorkerProperties.Supabase supabase = properties.supabase();
        this.restClient = restClientBuilder
                .requestFactory(HttpRequestFactories.withTimeouts(supabase.connectTimeout(), supabase.requestTimeout()))
                .baseUrl(supabase.url())
                .defaultHeader("apikey", supabase.serviceRoleKey())
                .defaultHeader("Authorization", "Bearer " + supabase.serviceRoleKey())
                .build();
    }

    /** Deletes the auth user. A user that is already gone counts as success, so retries are safe. */
    public void deleteUser(UserId userId) {
        restClient.delete()
                .uri("/auth/v1/admin/users/{userId}", userId.value())
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.NOT_FOUND.value(), (request, response) -> { })
                .toBodilessEntity();
    }
}
