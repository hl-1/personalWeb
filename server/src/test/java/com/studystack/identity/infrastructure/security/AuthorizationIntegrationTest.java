package com.studystack.identity.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccount;
import com.studystack.identity.domain.UserAccountRepository;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "studystack.identity.security.admin-github-ids=70002",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
class AuthorizationIntegrationTest {

    private static final String ADMIN_PATH = "/api/v1/admin/not-implemented";
    private static final String SESSION_COOKIE = "STUDYSTACK_SESSION";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.7-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserAccountRepository userAccounts;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("delete from identity_external_identity");
        jdbcTemplate.update("delete from identity_user_account");
    }

    @Test
    void anonymousAdminRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get(ADMIN_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonAdminCannotUseAdminAuthorityStoredInSession() throws Exception {
        UserAccount user = persist(AccountStatus.ACTIVE);
        OAuth2AuthenticationToken storedAuthentication = oauthAuthentication(
                user,
                "70001",
                List.of("ROLE_USER", "ROLE_ADMIN"));

        mockMvc.perform(get(ADMIN_PATH).with(authentication(storedAuthentication)))
                .andExpect(status().isForbidden());
    }

    @Test
    void configuredAdminIsAuthorizedWithoutDatabaseRoleSnapshot() throws Exception {
        UserAccount admin = persist(AccountStatus.ACTIVE);
        OAuth2AuthenticationToken storedAuthentication = oauthAuthentication(
                admin,
                "70002",
                List.of("ROLE_USER"));

        mockMvc.perform(get(ADMIN_PATH).with(authentication(storedAuthentication)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/auth/me").with(authentication(storedAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"))
                .andExpect(jsonPath("$.user.roles[1]").value("ADMIN"))
                .andExpect(jsonPath("$.user.roles.length()").value(2));
    }

    @Test
    void disabledAccountInvalidatesSessionAndReturnsUnauthorized() throws Exception {
        UserAccount disabled = persist(AccountStatus.DISABLED);
        OAuth2AuthenticationToken storedAuthentication = oauthAuthentication(
                disabled,
                "70002",
                List.of("ROLE_USER"));
        MockHttpSession session = new MockHttpSession();
        SecurityContext storedContext = SecurityContextHolder.createEmptyContext();
        storedContext.setAuthentication(storedAuthentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                storedContext);

        mockMvc.perform(get(ADMIN_PATH)
                        .session(session)
                        .cookie(new Cookie(SESSION_COOKIE, "disabled-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(SESSION_COOKIE, 0));

        assertTrue(session.isInvalid());
    }

    private UserAccount persist(AccountStatus status) {
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        UUID id = UUID.randomUUID();
        return userAccounts.save(new UserAccount(
                id,
                "user-" + id,
                "Test User",
                null,
                status,
                timestamp,
                timestamp,
                timestamp));
    }

    private OAuth2AuthenticationToken oauthAuthentication(
            UserAccount account,
            String providerSubject,
            List<String> authorityNames) {
        StudyStackPrincipal principal = new StudyStackPrincipal(
                account.id(),
                providerSubject,
                account.login(),
                account.displayName(),
                account.avatarUrl());
        return new OAuth2AuthenticationToken(
                principal,
                authorityNames.stream().map(SimpleGrantedAuthority::new).toList(),
                "github");
    }
}
