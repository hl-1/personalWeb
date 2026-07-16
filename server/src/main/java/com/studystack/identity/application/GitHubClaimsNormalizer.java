package com.studystack.identity.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class GitHubClaimsNormalizer {

    private static final int MAX_PROFILE_TEXT_LENGTH = 255;
    private static final int MAX_AVATAR_URL_LENGTH = 2048;
    private static final Pattern DECIMAL_ID = Pattern.compile("[0-9]+");

    public GitHubIdentityClaims normalize(Map<String, Object> claims) {
        if (claims == null) {
            throw invalid("claims", InvalidGitHubClaimsException.Reason.REQUIRED);
        }

        String providerSubject = normalizeId(claims.get("id"));
        String login = normalizeRequiredText(claims.get("login"), "login");
        String displayName = normalizeDisplayName(claims.get("name"), login);
        String avatarUrl = normalizeAvatarUrl(claims.get("avatar_url"));
        return new GitHubIdentityClaims(providerSubject, login, displayName, avatarUrl);
    }

    private String normalizeId(Object value) {
        if (value == null) {
            throw invalid("id", InvalidGitHubClaimsException.Reason.REQUIRED);
        }

        long id;
        if (value instanceof Long longValue) {
            id = longValue;
        } else if (value instanceof Integer integerValue) {
            id = integerValue.longValue();
        } else if (value instanceof String stringValue) {
            if (!DECIMAL_ID.matcher(stringValue).matches()) {
                throw invalid("id", InvalidGitHubClaimsException.Reason.INVALID_VALUE);
            }
            try {
                id = Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                throw invalid("id", InvalidGitHubClaimsException.Reason.INVALID_VALUE);
            }
        } else {
            throw invalid("id", InvalidGitHubClaimsException.Reason.INVALID_TYPE);
        }

        if (id <= 0) {
            throw invalid("id", InvalidGitHubClaimsException.Reason.INVALID_VALUE);
        }
        return Long.toString(id);
    }

    private String normalizeRequiredText(Object value, String fieldName) {
        if (value == null) {
            throw invalid(fieldName, InvalidGitHubClaimsException.Reason.REQUIRED);
        }
        if (!(value instanceof String stringValue)) {
            throw invalid(fieldName, InvalidGitHubClaimsException.Reason.INVALID_TYPE);
        }

        String normalized = stringValue.trim();
        if (normalized.isEmpty()) {
            throw invalid(fieldName, InvalidGitHubClaimsException.Reason.INVALID_VALUE);
        }
        if (normalized.length() > MAX_PROFILE_TEXT_LENGTH) {
            throw invalid(fieldName, InvalidGitHubClaimsException.Reason.TOO_LONG);
        }
        return normalized;
    }

    private String normalizeDisplayName(Object value, String login) {
        if (value == null) {
            return login;
        }
        if (!(value instanceof String stringValue)) {
            throw invalid("name", InvalidGitHubClaimsException.Reason.INVALID_TYPE);
        }

        String normalized = stringValue.trim();
        if (normalized.isEmpty()) {
            return login;
        }
        if (normalized.length() > MAX_PROFILE_TEXT_LENGTH) {
            throw invalid("name", InvalidGitHubClaimsException.Reason.TOO_LONG);
        }
        return normalized;
    }

    private String normalizeAvatarUrl(Object value) {
        if (!(value instanceof String stringValue)) {
            return null;
        }

        String normalized = stringValue.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_AVATAR_URL_LENGTH) {
            return null;
        }

        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            return normalized;
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private InvalidGitHubClaimsException invalid(
            String fieldName,
            InvalidGitHubClaimsException.Reason reason) {
        return new InvalidGitHubClaimsException(fieldName, reason);
    }
}
