package com.studystack.portfolio.application.admin;

import com.studystack.portfolio.application.admin.PortfolioAdminViews.AdminException;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Failure;
import com.studystack.portfolio.application.admin.PortfolioAdminViews.Profile;
import com.studystack.portfolio.domain.PortfolioProfile;
import com.studystack.portfolio.domain.PortfolioProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfileAdminService {

    private static final String PROFILE_PRIMARY_KEY = "pk_portfolio_profile";

    private final PortfolioProfileRepository profiles;

    ProfileAdminService(PortfolioProfileRepository profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles is required");
    }

    @Transactional(readOnly = true)
    public Profile find() {
        return toView(findProfile());
    }

    public Profile upsert(Command command) {
        Objects.requireNonNull(command, "command is required");
        if (command.version() == null) {
            if (profiles.findById(PortfolioProfile.SINGLETON_ID).isPresent()) {
                throw failure(Failure.STALE_VERSION);
            }
            return create(command);
        }

        PortfolioProfile profile = findProfile();
        requireVersion(profile.version(), command.version());
        profile.revise(
                command.displayName(),
                command.headline(),
                command.bioMarkdown(),
                command.seoDescription(),
                now());
        return persist(profile);
    }

    private Profile create(Command command) {
        PortfolioProfile profile = new PortfolioProfile(
                command.displayName(),
                command.headline(),
                command.bioMarkdown(),
                command.seoDescription(),
                now());
        try {
            return persist(profile);
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, PROFILE_PRIMARY_KEY)) {
                throw failure(Failure.STALE_VERSION);
            }
            throw exception;
        }
    }

    private Profile persist(PortfolioProfile profile) {
        try {
            profiles.save(profile);
            profiles.flush();
            return toView(profile);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private PortfolioProfile findProfile() {
        return profiles.findById(PortfolioProfile.SINGLETON_ID)
                .orElseThrow(() -> failure(Failure.NOT_FOUND));
    }

    private Profile toView(PortfolioProfile profile) {
        return new Profile(
                profile.id(),
                profile.displayName(),
                profile.headline(),
                profile.bioMarkdown(),
                profile.seoDescription(),
                profile.createdAt(),
                profile.updatedAt(),
                profile.version());
    }

    private static void requireVersion(Long actual, long expected) {
        if (actual == null || actual != expected) {
            throw failure(Failure.STALE_VERSION);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean hasConstraint(Throwable failure, String constraintName) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException constraint
                    && constraintName.equals(constraint.getConstraintName())) {
                return true;
            }
            if (current == current.getCause()) {
                return false;
            }
        }
        return false;
    }

    private static AdminException failure(Failure failure) {
        return new AdminException(failure);
    }

    public record Command(
            String displayName,
            String headline,
            String bioMarkdown,
            String seoDescription,
            Long version) {

        public Command {
            if (version != null && version < 0) {
                throw new IllegalArgumentException("version must be non-negative");
            }
        }
    }
}
