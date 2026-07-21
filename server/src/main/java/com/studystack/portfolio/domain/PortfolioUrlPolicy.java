package com.studystack.portfolio.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class PortfolioUrlPolicy {

    private static final int MAXIMUM_LENGTH = 2_048;

    private PortfolioUrlPolicy() {
    }

    static String normalizeOptional(String value, String field) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isEmpty()
                || candidate.length() > MAXIMUM_LENGTH
                || candidate.chars().anyMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character))) {
            throw invalidUrl(field);
        }

        try {
            URI parsed = new URI(candidate).normalize();
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || parsed.getHost() == null
                    || parsed.getHost().isBlank()
                    || parsed.getRawUserInfo() != null) {
                throw invalidUrl(field);
            }
            String normalized = normalizedHttpsUrl(parsed);
            if (normalized.length() > MAXIMUM_LENGTH) {
                throw invalidUrl(field);
            }
            return normalized;
        } catch (URISyntaxException exception) {
            throw invalidUrl(field);
        }
    }

    private static String normalizedHttpsUrl(URI uri) {
        StringBuilder normalized = new StringBuilder("https://")
                .append(uri.getRawAuthority().toLowerCase(Locale.ROOT));
        append(normalized, uri.getRawPath(), null);
        append(normalized, uri.getRawQuery(), "?");
        append(normalized, uri.getRawFragment(), "#");
        return normalized.toString();
    }

    private static void append(StringBuilder target, String value, String prefix) {
        if (value != null) {
            if (prefix != null) {
                target.append(prefix);
            }
            target.append(value);
        }
    }

    private static IllegalArgumentException invalidUrl(String field) {
        return new IllegalArgumentException(field + " must be an HTTPS URL without credentials");
    }
}
