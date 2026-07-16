package com.studystack.identity.infrastructure.security;

import com.studystack.identity.application.AdminGithubIdPolicy;
import com.studystack.identity.domain.AccountStatus;
import com.studystack.identity.domain.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public final class ActiveAccountFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccounts;
    private final AdminGithubIdPolicy adminPolicy;
    private final LogoutHandler logoutHandler;

    public ActiveAccountFilter(
            UserAccountRepository userAccounts,
            AdminGithubIdPolicy adminPolicy,
            LogoutHandler logoutHandler) {
        this.userAccounts = userAccounts;
        this.adminPolicy = adminPolicy;
        this.logoutHandler = logoutHandler;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof StudyStackPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean active = userAccounts.findById(principal.userId())
                .map(account -> account.status() == AccountStatus.ACTIVE)
                .orElse(false);
        if (!active) {
            logoutHandler.logout(request, response, authentication);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        OAuth2AuthenticationToken refreshed = new OAuth2AuthenticationToken(
                principal,
                adminPolicy.rolesFor(principal.providerSubject()).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList(),
                authentication instanceof OAuth2AuthenticationToken oauth
                        ? oauth.getAuthorizedClientRegistrationId()
                        : "github");
        refreshed.setDetails(authentication.getDetails());
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(refreshed);
        SecurityContextHolder.setContext(requestContext);

        filterChain.doFilter(request, response);
    }
}
