package com.studystack.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studystack.identity.application.AdminGithubIdPolicy;
import com.studystack.identity.config.IdentitySecurityProperties;
import com.studystack.identity.domain.UserAccountRepository;
import com.studystack.shared.web.PublicApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    LogoutHandler authLogoutHandler(IdentitySecurityProperties properties) {
        return new CompositeLogoutHandler(
                new SecurityContextLogoutHandler(),
                new CookieClearingLogoutHandler(properties.cookie().name()));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<GitHubOAuth2UserService> userServices,
            ObjectProvider<UserAccountRepository> userAccounts,
            AdminGithubIdPolicy adminPolicy,
            @Qualifier("authLogoutHandler") LogoutHandler logoutHandler,
            OAuthLoginSuccessHandler successHandler,
            OAuthLoginFailureHandler failureHandler,
            ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/sitemap.xml",
                                "/robots.txt",
                                "/api/v1/articles",
                                "/api/v1/articles/**",
                                "/api/v1/categories",
                                "/api/v1/tags",
                                "/api/v1/portfolio/**")
                        .permitAll()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "unauthorized",
                                "Authentication required",
                                "Authentication is required to access this resource"))
                        .accessDeniedHandler((request, response, exception) -> {
                            boolean csrfFailure = exception instanceof org.springframework.security.web.csrf.CsrfException;
                            writeProblem(
                                    objectMapper,
                                    request,
                                    response,
                                    HttpStatus.FORBIDDEN,
                                    csrfFailure ? "csrf_failed" : "forbidden",
                                    csrfFailure ? "CSRF validation failed" : "Access forbidden",
                                    csrfFailure
                                            ? "A valid CSRF token is required for this request"
                                            : "Administrator privileges are required to access this resource");
                        }))
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(request -> userServices.getObject().loadUser(request)))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));
        userAccounts.ifAvailable(repository -> http.addFilterBefore(
                new ActiveAccountFilter(repository, adminPolicy, logoutHandler),
                AuthorizationFilter.class));
        return http.build();
    }

    private static void writeProblem(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String title,
            String detail) throws IOException {
        ProblemDetail problem = PublicApiExceptionHandler.problem(
                status, code, title, detail, URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
