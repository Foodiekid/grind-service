package com.grindandtrain.core.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import com.grindandtrain.common.domain.UserId;
import com.grindandtrain.core.common.persistence.UserTransactionTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The guarantees the schema itself gives, checked as the application role: records belong to an existing account,
 * inline ciphertext stays small, the 32 parts of {@code records} can't be read around row-level security, and a
 * sync-down query reads exactly one part.
 *
 * @author Dheeraj_Edupuganti
 */
class DatabaseSchemaTest {

    private static final Pattern PART = Pattern.compile("records_p\\d{2}");

    private JdbcClient jdbcClient;
    private UserTransactionTemplate userTransactions;

    @BeforeEach
    void setUp() {
        var dataSource = GrindTestDatabase.get().appDataSource();
        jdbcClient = JdbcClient.create(dataSource);
        userTransactions = new UserTransactionTemplate(jdbcClient,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @Test
    void recordsAreSplitIntoThirtyTwoParts() {
        long parts = jdbcClient.sql("select count(*) from pg_inherits where inhparent = 'grind.records'::regclass")
                .query(Long.class).single();

        assertThat(parts).isEqualTo(32);
    }

    @Test
    void aRecordForAMissingAccountIsRefused() {
        UserId user = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> userTransactions.executeWithoutResult(user, () -> insertRecord(user, 16)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("records_user_id_fkey");
    }

    @Test
    void inlineCiphertextOverFourKibibytesIsRefused() {
        UserId user = newAccount();

        userTransactions.executeWithoutResult(user, () -> insertRecord(user, 4096));
        assertThatThrownBy(() -> userTransactions.executeWithoutResult(user, () -> insertRecord(user, 4097)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("records_inline_ct_check");
    }

    @Test
    void thePartsCantBeQueriedDirectly() {
        assertThatThrownBy(() -> jdbcClient.sql("select count(*) from records_p00").query(Long.class).single())
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("permission denied for table records_p00");
    }

    @Test
    void syncDownReadsExactlyOnePart() {
        UserId user = newAccount();

        List<String> plan = userTransactions.execute(user, () -> jdbcClient.sql("""
                explain (costs off)
                select id, seq from records where user_id = :userId and seq > 0 order by seq limit 200""")
                .param("userId", user.value())
                .query(String.class)
                .list());

        assertThat(partsIn(plan)).hasSize(1);
        assertThat(String.join("\n", plan)).contains("Index Scan");
    }

    @Test
    void rowLevelSecurityAloneAlsoNarrowsToOnePart() {
        UserId user = newAccount();

        List<String> plan = userTransactions.execute(user, () -> jdbcClient
                .sql("explain (analyze, costs off, timing off, summary off) select count(*) from records")
                .query(String.class)
                .list());

        assertThat(String.join("\n", plan)).contains("Subplans Removed: 31");
    }

    private UserId newAccount() {
        UserId user = UserId.of(UUID.randomUUID());
        userTransactions.executeWithoutResult(user, () -> jdbcClient
                .sql("insert into accounts (user_id) values (:userId)").param("userId", user.value()).update());
        return user;
    }

    private void insertRecord(UserId user, int inlineBytes) {
        jdbcClient.sql("""
                insert into records (user_id, id, rev, seq, deleted, inline_ct, wrapped_dek)
                values (:userId, :id, 1, nextval('record_seq'), false, :inline, 'wrapped-key')""")
                .param("userId", user.value())
                .param("id", UUID.randomUUID())
                .param("inline", new byte[inlineBytes])
                .update();
    }

    private static List<String> partsIn(List<String> plan) {
        return plan.stream()
                .flatMap(line -> PART.matcher(line).results())
                .map(MatchResult::group)
                .distinct()
                .toList();
    }
}
