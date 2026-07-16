package com.studystack.identity.infrastructure.security;

import com.studystack.identity.application.AdminGithubIdPolicy;
import com.studystack.identity.config.IdentitySecurityProperties;
import com.studystack.identity.domain.UserAccountRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
            OAuthLoginFailureHandler failureHandler) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
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
}
