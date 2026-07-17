package com.studystack.shared.slug;

import java.util.Objects;
import org.springframework.modulith.NamedInterface;

@NamedInterface("slug")
public final class Slug {

    private final String value;

    Slug(String value) {
        this.value = Objects.requireNonNull(value, "value is required");
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof Slug other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
