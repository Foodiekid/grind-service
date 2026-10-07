package com.grindandtrain.common.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.grindandtrain.common.error.ErrorCode;
import com.grindandtrain.common.web.ProblemDetailFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The app's error-code catalog ({@code grind/errors/v1/error-codes.json} in grind-contract), checked against the codes a
 * service really returns. The app turns every problem code into a message and an action from that file, so a code the
 * file doesn't list would reach users as a generic error; a service test fails the build instead.
 * <p>
 * Each service checks the owners whose codes it can return: every code of those owners is listed with the status the
 * server sends, and the file lists nothing for those owners that the code doesn't define.
 *
 * @author Dheeraj_Edupuganti
 */
public final class ErrorCatalog {

    private static final String LOCATION = "grind/errors/v1/error-codes.json";

    /**
     * Client errors the app may retry after doing something first: sign in again (401), fetch the newer data (409),
     * wait for an upload (422), update the app (426). Any other 4xx fails again unchanged, so it must never be retried.
     */
    private static final Set<Integer> CLIENT_ERRORS_RETRIED_AFTER_ACTION = Set.of(401, 409, 422, 426);

    private ErrorCatalog() {
    }

    /** One entry of the catalog. */
    public record Entry(String code, String owner, int status, String retry, String action, String message) {
    }

    /** Every entry, in file order. */
    public static List<Entry> entries() {
        JsonNode root = read();
        List<Entry> entries = new ArrayList<>();
        for (JsonNode node : root.get("codes")) {
            entries.add(new Entry(node.get("code").asString(), node.get("owner").asString(), node.get("status").asInt(),
                    node.get("retry").asString(), node.get("action").asString(), node.get("message").asString()));
        }
        return entries;
    }

    /**
     * Fails unless the catalog matches {@code codes} exactly for {@code owners}: same codes, same HTTP status, a known
     * retry rule and action, and a message for users.
     *
     * @param owners the catalog owners these codes belong to ("platform", "core", "partner")
     * @param codes  every error code those owners define
     */
    public static void assertDescribes(Set<String> owners, List<? extends ErrorCode> codes) {
        JsonNode root = read();
        Set<String> retries = names(root.get("retry"));
        Set<String> actions = names(root.get("action"));
        List<Entry> all = entries();

        assertThat(all).extracting(Entry::code).doesNotHaveDuplicates();
        Map<String, Entry> byCode = all.stream().collect(Collectors.toMap(Entry::code, Function.identity()));

        for (ErrorCode code : codes) {
            Entry entry = byCode.get(code.code());
            assertThat(entry).as("catalog entry for %s", code.code()).isNotNull();
            assertThat(owners).as("owner of %s", code.code()).contains(entry.owner());
            assertThat(entry.status()).as("status of %s", code.code())
                    .isEqualTo(ProblemDetailFactory.statusOf(code.category()).value());
        }

        Set<String> defined = codes.stream().map(ErrorCode::code).collect(Collectors.toSet());
        for (Entry entry : all) {
            assertThat(retries).as("retry rule of %s", entry.code()).contains(entry.retry());
            assertThat(actions).as("action of %s", entry.code()).contains(entry.action());
            assertThat(entry.message()).as("message of %s", entry.code()).isNotBlank().doesNotContain("—");
            if (owners.contains(entry.owner())) {
                assertThat(defined).as("%s is listed in the catalog but no longer defined", entry.code()).contains(entry.code());
            }
            assertRetryRuleFits(entry);
        }
    }

    /** A failure that can heal is retried later; a client error that can't heal is never retried (no retry loops). */
    private static void assertRetryRuleFits(Entry entry) {
        boolean isServiceUnavailable = entry.status() == 503;
        boolean isClientError = entry.status() >= 400 && entry.status() < 500;
        boolean healsOnlyAfterAnAction = CLIENT_ERRORS_RETRIED_AFTER_ACTION.contains(entry.status());

        if (isServiceUnavailable) {
            assertThat(entry.retry()).as("retry of %s", entry.code()).isEqualTo("later");
        }
        if (isClientError && !healsOnlyAfterAnAction) {
            assertThat(entry.retry()).as("retry of %s", entry.code()).isEqualTo("never");
        }
    }

    private static Set<String> names(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.propertyNames().forEach(names::add);
        return names;
    }

    private static JsonNode read() {
        try (InputStream in = ErrorCatalog.class.getClassLoader().getResourceAsStream(LOCATION)) {
            assertThat(in).as("%s on the classpath (grind-contract)", LOCATION).isNotNull();
            return JsonMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
