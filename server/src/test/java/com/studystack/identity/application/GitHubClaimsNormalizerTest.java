package com.studystack.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.stereotype.Component;

class GitHubClaimsNormalizerTest {

    private final GitHubClaimsNormalizer normalizer = new GitHubClaimsNormalizer();

    @ParameterizedTest
    @MethodSource("validIds")
    void normalizesSupportedIdRepresentations(Object id, String expectedSubject) {
        Map<String, Object> claims = validClaims();
        claims.put("id", id);

        GitHubIdentityClaims normalized = normalizer.normalize(claims);

        assertEquals(expectedSubject, normalized.providerSubject());
    }

    static Stream<Arguments> validIds() {
        return Stream.of(
                Arguments.of(42L, "42"),
                Arguments.of(42, "42"),
                Arguments.of("42", "42"),
                Arguments.of("00042", "42"),
                Arguments.of(Long.MAX_VALUE, Long.toString(Long.MAX_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void rejectsInvalidIds(Object id, InvalidGitHubClaimsException.Reason reason) {
        Map<String, Object> claims = validClaims();
        claims.put("id", id);

        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(claims));

        assertEquals("id", exception.fieldName());
        assertEquals(reason, exception.reason());
    }

    static Stream<Arguments> invalidIds() {
        return Stream.of(
                Arguments.of(0L, InvalidGitHubClaimsException.Reason.INVALID_VALUE),
                Arguments.of(-1L, InvalidGitHubClaimsException.Reason.INVALID_VALUE),
                Arguments.of(1.0d, InvalidGitHubClaimsException.Reason.INVALID_TYPE),
                Arguments.of(true, InvalidGitHubClaimsException.Reason.INVALID_TYPE),
                Arguments.of("9223372036854775808", InvalidGitHubClaimsException.Reason.INVALID_VALUE),
                Arguments.of("not-a-number", InvalidGitHubClaimsException.Reason.INVALID_VALUE),
                Arguments.of(" 42 ", InvalidGitHubClaimsException.Reason.INVALID_VALUE),
                Arguments.of("+42", InvalidGitHubClaimsException.Reason.INVALID_VALUE));
    }

    @Test
    void rejectsMissingId() {
        Map<String, Object> claims = validClaims();
        claims.remove("id");

        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(claims));

        assertEquals("id", exception.fieldName());
        assertEquals(InvalidGitHubClaimsException.Reason.REQUIRED, exception.reason());
    }

    @Test
    void rejectsMissingClaims() {
        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(null));

        assertEquals("claims", exception.fieldName());
        assertEquals(InvalidGitHubClaimsException.Reason.REQUIRED, exception.reason());
    }

    @Test
    void trimsLoginAndName() {
        Map<String, Object> claims = validClaims();
        claims.put("login", "  octocat  ");
        claims.put("name", "  The Octocat  ");

        GitHubIdentityClaims normalized = normalizer.normalize(claims);

        assertEquals("octocat", normalized.login());
        assertEquals("The Octocat", normalized.displayName());
    }

    @Test
    void rejectsBlankLogin() {
        Map<String, Object> claims = validClaims();
        claims.put("login", "   ");

        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(claims));

        assertEquals("login", exception.fieldName());
        assertEquals(InvalidGitHubClaimsException.Reason.INVALID_VALUE, exception.reason());
    }

    @Test
    void rejectsMissingWrongTypeAndOverlongLogin() {
        Map<String, Object> missingLogin = validClaims();
        missingLogin.remove("login");
        Map<String, Object> wrongTypeLogin = validClaims();
        wrongTypeLogin.put("login", 123);
        Map<String, Object> overlongLogin = validClaims();
        overlongLogin.put("login", "a".repeat(256));

        assertInvalidClaim(
                missingLogin, "login", InvalidGitHubClaimsException.Reason.REQUIRED);
        assertInvalidClaim(
                wrongTypeLogin, "login", InvalidGitHubClaimsException.Reason.INVALID_TYPE);
        assertInvalidClaim(
                overlongLogin, "login", InvalidGitHubClaimsException.Reason.TOO_LONG);
    }

    @Test
    void fallsBackToLoginWhenNameIsMissingOrBlank() {
        Map<String, Object> missingName = validClaims();
        missingName.remove("name");
        Map<String, Object> blankName = validClaims();
        blankName.put("name", "  ");

        assertEquals("octocat", normalizer.normalize(missingName).displayName());
        assertEquals("octocat", normalizer.normalize(blankName).displayName());
    }

    @Test
    void rejectsWrongTypeAndOverlongName() {
        Map<String, Object> wrongTypeName = validClaims();
        wrongTypeName.put("name", 123);
        Map<String, Object> overlongName = validClaims();
        overlongName.put("name", "a".repeat(256));

        assertInvalidClaim(
                wrongTypeName, "name", InvalidGitHubClaimsException.Reason.INVALID_TYPE);
        assertInvalidClaim(
                overlongName, "name", InvalidGitHubClaimsException.Reason.TOO_LONG);
    }

    @Test
    void keepsOnlyAbsoluteHttpsAvatarUrls() {
        Map<String, Object> validAvatar = validClaims();
        validAvatar.put("avatar_url", "  https://avatars.example/octocat.png  ");
        Map<String, Object> httpAvatar = validClaims();
        httpAvatar.put("avatar_url", "http://avatars.example/octocat.png");
        Map<String, Object> relativeAvatar = validClaims();
        relativeAvatar.put("avatar_url", "/octocat.png");
        Map<String, Object> invalidAvatar = validClaims();
        invalidAvatar.put("avatar_url", "https://[invalid");
        Map<String, Object> missingAvatar = validClaims();
        missingAvatar.remove("avatar_url");
        Map<String, Object> wrongTypeAvatar = validClaims();
        wrongTypeAvatar.put("avatar_url", 123);
        Map<String, Object> overlongAvatar = validClaims();
        overlongAvatar.put("avatar_url", "https://avatars.example/" + "a".repeat(2025));

        assertEquals(
                "https://avatars.example/octocat.png",
                normalizer.normalize(validAvatar).avatarUrl());
        assertNull(normalizer.normalize(httpAvatar).avatarUrl());
        assertNull(normalizer.normalize(relativeAvatar).avatarUrl());
        assertNull(normalizer.normalize(invalidAvatar).avatarUrl());
        assertNull(normalizer.normalize(missingAvatar).avatarUrl());
        assertNull(normalizer.normalize(wrongTypeAvatar).avatarUrl());
        assertNull(normalizer.normalize(overlongAvatar).avatarUrl());
    }

    @Test
    void doesNotMutateInputClaims() {
        Map<String, Object> claims = validClaims();
        Map<String, Object> original = Map.copyOf(claims);

        normalizer.normalize(claims);

        assertEquals(original, claims);
    }

    @Test
    void redactsClaimsAndInvalidValuesFromTextRepresentations() {
        String subject = "987654321";
        GitHubIdentityClaims normalized = new GitHubIdentityClaims(
                subject, "octocat", "The Octocat", "https://avatars.example/octocat.png");
        Map<String, Object> invalidClaims = validClaims();
        String invalidId = "sensitive-invalid-id";
        invalidClaims.put("id", invalidId);

        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(invalidClaims));

        assertEquals("GitHubIdentityClaims[redacted]", normalized.toString());
        assertFalse(normalized.toString().contains(subject));
        assertFalse(exception.getMessage().contains(invalidId));
        assertNull(exception.getCause());
    }

    @Test
    void isAvailableAsAStatelessSpringComponent() {
        assertTrue(GitHubClaimsNormalizer.class.isAnnotationPresent(Component.class));
    }

    private void assertInvalidClaim(
            Map<String, Object> claims,
            String fieldName,
            InvalidGitHubClaimsException.Reason reason) {
        InvalidGitHubClaimsException exception = assertThrows(
                InvalidGitHubClaimsException.class,
                () -> normalizer.normalize(claims));

        assertEquals(fieldName, exception.fieldName());
        assertEquals(reason, exception.reason());
    }

    private Map<String, Object> validClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 1L);
        claims.put("login", "octocat");
        claims.put("name", "The Octocat");
        claims.put("avatar_url", "https://avatars.example/octocat.png");
        return claims;
    }
}
