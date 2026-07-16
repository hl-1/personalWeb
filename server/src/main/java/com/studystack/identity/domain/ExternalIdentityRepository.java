package com.studystack.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ExternalIdentityRepository extends Repository<ExternalIdentity, UUID> {

    ExternalIdentity save(ExternalIdentity identity);

    Optional<ExternalIdentity> findByProviderAndProviderSubject(
            String provider,
            String providerSubject);
}
