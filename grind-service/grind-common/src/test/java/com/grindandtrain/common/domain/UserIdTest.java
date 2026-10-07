package com.grindandtrain.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Checks that a user id never prints as the real id, and that its pseudonym is stable.
 *
 * @author Dheeraj_Edupuganti
 */
class UserIdTest {

    private static final UUID ID = UUID.fromString("3f2a8c1e-9b4d-4e7a-8f6c-2d1e0b9a7c5f");

    @Test
    void toStringShowsOnlyThePseudonym() {
        UserId userId = UserId.of(ID);
        assertThat(userId.toString()).doesNotContain(ID.toString()).isEqualTo("user:" + userId.pseudonym());
    }

    @Test
    void pseudonymIsStableAndShort() {
        assertThat(UserId.of(ID).pseudonym())
                .isEqualTo(UserId.of(ID).pseudonym())
                .matches("[0-9a-f]{16}")
                .isNotEqualTo(UserId.of(UUID.randomUUID()).pseudonym());
    }

    @Test
    void parseRejectsNonUuidSubjects() {
        assertThatThrownBy(() -> UserId.parse("service-account")).isInstanceOf(IllegalArgumentException.class);
    }
}
