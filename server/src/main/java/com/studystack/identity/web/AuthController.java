package com.studystack.identity.web;

import com.studystack.identity.infrastructure.security.StudyStackPrincipal;
import com.studystack.shared.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Session authentication state and lifecycle")
public final class AuthController {

    private static final List<String> APPROVED_ROLES = List.of("USER", "ADMIN");

    private final LogoutHandler logoutHandler;

    public AuthController(@Qualifier("authLogoutHandler") LogoutHandler logoutHandler) {
        this.logoutHandler = logoutHandler;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authentication state")
    @ApiResponse(
            responseCode = "200",
            description = "Current authentication state",
            content = @Content(schema = @Schema(implementation = AuthStateResponse.class)))
    public AuthStateResponse currentUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof StudyStackPrincipal principal)) {
            return new AuthStateResponse(false, null);
        }
        return new AuthStateResponse(true, toResponse(principal, authentication));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF token for state-changing requests")
    @ApiResponse(
            responseCode = "200",
            description = "Current CSRF token",
            content = @Content(schema = @Schema(implementation = CsrfTokenResponse.class)))
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfTokenResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "End the current session",
            security = @SecurityRequirement(
                    name = OpenApiConfiguration.SESSION_COOKIE_SECURITY_SCHEME))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session ended", content = @Content),
            @ApiResponse(responseCode = "403", description = "CSRF token missing or invalid", content = @Content)
    })
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
    }

    private AuthUserResponse toResponse(
            StudyStackPrincipal principal,
            Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        List<String> roles = APPROVED_ROLES.stream()
                .filter(role -> authorities.contains("ROLE_" + role))
                .toList();
        return new AuthUserResponse(
                principal.userId(),
                principal.login(),
                principal.displayName(),
                principal.avatarUrl(),
                roles);
    }
}
