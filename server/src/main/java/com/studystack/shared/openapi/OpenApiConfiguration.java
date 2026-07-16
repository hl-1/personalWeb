package com.studystack.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "StudyStack API",
                version = "P1",
                description = "StudyStack P1 identity API contract"))
@SecurityScheme(
        name = OpenApiConfiguration.SESSION_COOKIE_SECURITY_SCHEME,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = OpenApiConfiguration.SESSION_COOKIE_NAME,
        description = "Server-managed StudyStack session cookie")
public class OpenApiConfiguration {

    public static final String SESSION_COOKIE_SECURITY_SCHEME = "sessionCookie";
    public static final String SESSION_COOKIE_NAME = "STUDYSTACK_SESSION";
}
