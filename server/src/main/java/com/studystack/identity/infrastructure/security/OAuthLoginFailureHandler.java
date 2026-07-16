package com.studystack.identity.infrastructure.security;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public final class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    private static final Set<String> APPROVED_FAILURES = Set.of(
            "invalid_profile",
            "identity_conflict",
            "account_disabled",
            "login_failed");

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        clearAuthenticationException(request.getSession(false));
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader(HttpHeaders.LOCATION, "/login?error=" + failureCode(exception));
    }

    private String failureCode(AuthenticationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OAuth2AuthenticationException oauthException) {
                String providerCode = oauthException.getError().getErrorCode();
                if ("access_denied".equals(providerCode)) {
                    return "oauth_denied";
                }
                if (APPROVED_FAILURES.contains(providerCode)) {
                    return providerCode;
                }
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return "login_failed";
    }

    private void clearAuthenticationException(HttpSession session) {
        if (session != null) {
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
    }
}
