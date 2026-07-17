package com.studystack.shared.slug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.stereotype.Component;

class SlugPolicyTest {

    private final SlugPolicy policy = new SlugPolicy();

    @ParameterizedTest
    @MethodSource("validSlugs")
    void createsCanonicalSlugs(String candidate, String expected) {
        Slug slug = policy.create(candidate);

        assertEquals(expected, slug.value());
    }

    static Stream<Arguments> validSlugs() {
        return Stream.of(
                Arguments.of("abc", "abc"),
                Arguments.of("a1-b2", "a1-b2"),
                Arguments.of("  JAVA-21  ", "java-21"),
                Arguments.of("a".repeat(120), "a".repeat(120)));
    }

    @ParameterizedTest
    @MethodSource("invalidSlugs")
    void rejectsValuesOutsideTheSlugContract(String candidate) {
        assertThrows(IllegalArgumentException.class, () -> policy.create(candidate));
    }

    static Stream<Arguments> invalidSlugs() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("ab"),
                Arguments.of("a".repeat(121)),
                Arguments.of("double--hyphen"),
                Arguments.of("-leading"),
                Arguments.of("trailing-"),
                Arguments.of("space inside"),
                Arguments.of("中文-slug"),
                Arguments.of("path/segment"),
                Arguments.of("../path"),
                Arguments.of("abc\u0000def"));
    }

    @Test
    void returnsThePublishedSlugForAnEquivalentRequestedValue() {
        Slug publishedSlug = policy.create("stable-slug");

        Slug result = policy.requireUnchangedAfterPublication(publishedSlug, "  STABLE-SLUG  ");

        assertSame(publishedSlug, result);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"new-slug", "not valid", "中文"})
    void reportsOneStableConflictForEveryPublishedSlugChange(String requestedValue) {
        Slug publishedSlug = policy.create("stable-slug");

        SlugPolicy.PublishedSlugConflictException failure = assertThrows(
                SlugPolicy.PublishedSlugConflictException.class,
                () -> policy.requireUnchangedAfterPublication(publishedSlug, requestedValue));

        assertEquals(SlugPolicy.PUBLISHED_SLUG_IMMUTABLE_CODE, failure.code());
        assertEquals("Published slug cannot be changed", failure.getMessage());
        assertFalse(failure.getMessage().contains(publishedSlug.value()));
    }

    @Test
    void exposesAnImmutableValueObjectAndStatelessSharedPolicy() {
        Slug first = policy.create("shared-slug");
        Slug second = policy.create("SHARED-SLUG");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(Modifier.isFinal(Slug.class.getModifiers()));
        assertTrue(Arrays.stream(Slug.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(Slug.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertTrue(SlugPolicy.class.isAnnotationPresent(Component.class));
    }
}
