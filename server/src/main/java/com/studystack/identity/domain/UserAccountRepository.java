package com.studystack.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface UserAccountRepository extends Repository<UserAccount, UUID> {

    UserAccount save(UserAccount account);

    Optional<UserAccount> findById(UUID id);
}
