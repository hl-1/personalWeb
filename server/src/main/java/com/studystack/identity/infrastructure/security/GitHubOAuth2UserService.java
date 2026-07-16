package com.studystack.identity.infrastructure.security;

import com.studystack.identity.application.AuthenticatedIdentity;
import com.studystack.identity.application.GitHubClaimsNormalizer;
import com.studystack.identity.application.GitHubIdentityClaims;
import com.studystack.identity.application.IdentityBindingService;
import com.studystack.identity.application.InvalidGitHubClaimsException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@Lazy
public final class GitHubOAuth2UserService
        implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String GITHUB = "github";

    private final GitHubClaimsNormalizer claimsNormalizer;
    private final IdentityBindingService bindingService;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Autowired
    public GitHubOAuth2UserService(
            GitHubClaimsNormalizer claimsNormalizer,
            IdentityBindingService bindingService) {
        this(claimsNormalizer, bindingService, new DefaultOAuth2UserService());
    }

    GitHubOAuth2UserService(
            GitHubClaimsNormalizer claimsNormalizer,
            IdentityBindingService bindingService,
            OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
        this.claimsNormalizer = claimsNormalizer;
        this.bindingService = bindingService;
        this.delegate = delegate;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        if (!GITHUB.equals(request.getClientRegistration().getRegistrationId())) {
            throw authenticationFailure("login_failed");
        }

        OAuth2User upstreamUser = delegate.loadUser(request);
        try {
            GitHubIdentityClaims claims = claimsNormalizer.normalize(upstreamUser.getAttributes());
            AuthenticatedIdentity identity = bindingService.bind(claims);
            return new StudyStackPrincipal(
                    identity.userId(),
                    identity.providerSubject(),
                    identity.login(),
                    identity.displayName(),
                    identity.avatarUrl());
        } catch (InvalidGitHubClaimsException exception) {
            throw authenticationFailure("invalid_profile");
        } catch (IdentityBindingService.AccountDisabledException exception) {
            throw authenticationFailure("account_disabled");
        } catch (DataIntegrityViolationException exception) {
            throw authenticationFailure("identity_conflict");
        } catch (OAuth2AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw authenticationFailure("login_failed");
        }
    }

    private OAuth2AuthenticationException authenticationFailure(String code) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(code), "GitHub login failed");
    }
}
