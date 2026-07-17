package com.studystack.shared.slug;

import java.io.Serial;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

@Component
@NamedInterface("slug")
public final class SlugPolicy {

    public static final String PUBLISHED_SLUG_IMMUTABLE_CODE = "published_slug_immutable";

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 120;
    private static final Pattern VALID_SLUG =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public Slug create(String candidate) {
        String normalized = normalize(candidate);
        if (normalized == null
                || normalized.length() < MIN_LENGTH
                || normalized.length() > MAX_LENGTH
                || !VALID_SLUG.matcher(normalized).matches()) {
            throw invalidSlug();
        }
        return new Slug(normalized);
    }

    public Slug requireUnchangedAfterPublication(Slug publishedSlug, String requestedValue) {
        Objects.requireNonNull(publishedSlug, "publishedSlug is required");
        String normalized = normalize(requestedValue);
        if (!publishedSlug.value().equals(normalized)) {
            throw new PublishedSlugConflictException();
        }
        return publishedSlug;
    }

    private static String normalize(String candidate) {
        if (candidate == null || containsControlCharacter(candidate)) {
            return null;
        }
        return candidate.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsControlCharacter(String candidate) {
        return candidate.chars().anyMatch(Character::isISOControl);
    }

    private static IllegalArgumentException invalidSlug() {
        return new IllegalArgumentException(
                "slug must contain 3 to 120 ASCII letters or digits separated by single hyphens");
    }

    public static final class PublishedSlugConflictException extends IllegalStateException {

        @Serial
        private static final long serialVersionUID = 1L;

        private PublishedSlugConflictException() {
            super("Published slug cannot be changed");
        }

        public String code() {
            return PUBLISHED_SLUG_IMMUTABLE_CODE;
        }
    }
}
