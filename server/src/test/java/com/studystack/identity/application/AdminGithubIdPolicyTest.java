package com.studystack.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.studystack.identity.config.IdentitySecurityProperties;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class AdminGithubIdPolicyTest {

    @Test
    void trimsCanonicalizesAndDeduplicatesAdminIdsWithStableRoles() {
        AdminGithubIdPolicy policy = policy(" 00042, 7,42,0007 ", "test");

        assertEquals(java.util.List.of("USER", "ADMIN"), policy.rolesFor("42"));
        assertEquals(java.util.List.of("USER", "ADMIN"), policy.rolesFor("7"));
        assertEquals(java.util.List.of("USER"), policy.rolesFor("8"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "test"})
    void allowsEmptyAdminListOutsideProduction(String profile) {
        AdminGithubIdPolicy policy = policy("   ", profile);

        assertFalse(policy.isAdmin("42"));
        assertEquals(java.util.List.of("USER"), policy.rolesFor("42"));
    }

    @ParameterizedTest
    @MethodSource("invalidAdminLists")
    void rejectsInvalidAdminListsWithoutEchoingConfiguredValues(String value) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> policy(value, "test"));

        assertTrue(failure.getMessage().contains(
                IdentitySecurityProperties.ADMIN_GITHUB_IDS_ENVIRONMENT_VARIABLE));
        assertFalse(failure.getMessage().contains(value));
    }

    @Test
    void productionRequiresNonEmptyAdminListWithoutEchoingValues() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> policy("   ", "prod"));

        assertTrue(failure.getMessage().contains(
                IdentitySecurityProperties.ADMIN_GITHUB_IDS_ENVIRONMENT_VARIABLE));
        assertFalse(failure.getMessage().contains("   "));
    }

    private AdminGithubIdPolicy policy(String rawIds, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new AdminGithubIdPolicy(properties(rawIds), environment);
    }

    private IdentitySecurityProperties properties(String rawIds) {
        return new IdentitySecurityProperties(
                rawIds,
                Duration.ofHours(24),
                new IdentitySecurityProperties.Cookie(
                        "STUDYSTACK_SESSION",
                        true,
                        "Lax",
                        "/",
                        false));
    }

    private static Stream<Arguments> invalidAdminLists() {
        return Stream.of(
                Arguments.of("1,,2"),
                Arguments.of(",1"),
                Arguments.of("1,"),
                Arguments.of("0"),
                Arguments.of("-1"),
                Arguments.of("1.5"),
                Arguments.of("not-a-number"),
                Arguments.of("9223372036854775808"),
                Arguments.of("１２"));
    }
}
