package com.studystack.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "StudyStack API",
                version = "P3",
                description = "StudyStack public and administrative API contract"))
@SecurityScheme(
        name = OpenApiConfiguration.SESSION_COOKIE_SECURITY_SCHEME,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = OpenApiConfiguration.SESSION_COOKIE_NAME,
        description = "Server-managed StudyStack session cookie")
public class OpenApiConfiguration {

    public static final String SESSION_COOKIE_SECURITY_SCHEME = "sessionCookie";
    public static final String SESSION_COOKIE_NAME = "STUDYSTACK_SESSION";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

    private static final Set<String> CREATE_PATHS = Set.of(
            "/api/v1/admin/articles",
            "/api/v1/admin/categories",
            "/api/v1/admin/tags",
            "/api/v1/admin/portfolio/projects",
            "/api/v1/admin/portfolio/skills",
            "/api/v1/admin/portfolio/experiences");

    @Bean
    OpenApiCustomizer adminApiContractCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.startsWith("/api/v1/admin/")) {
                    return;
                }
                pathItem.readOperationsMap().forEach((method, operation) -> customize(path, method, operation));
            });
        };
    }

    private static void customize(String path, HttpMethod method, Operation operation) {
        operation.addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE_SECURITY_SCHEME));
        if (method != HttpMethod.GET) {
            operation.addParametersItem(new Parameter()
                    .name(CSRF_HEADER_NAME)
                    .in("header")
                    .required(true)
                    .description("CSRF token")
                    .schema(new StringSchema()));
        }

        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        responses.forEach((status, apiResponse) -> {
            if (status.startsWith("2")
                    && apiResponse.getContent() != null
                    && !apiResponse.getContent().isEmpty()
                    && !apiResponse.getContent().containsKey("application/json")) {
                apiResponse.getContent().addMediaType(
                        "application/json", apiResponse.getContent().values().iterator().next());
            }
        });
        responses.addApiResponse("400", problemResponse("Invalid request"));
        responses.addApiResponse("401", problemResponse("Authentication required"));
        responses.addApiResponse("403", problemResponse("Administrator role or valid CSRF token required"));

        if (path.contains("{id}")) {
            responses.addApiResponse("404", problemResponse("Resource not found"));
            responses.addApiResponse("409", problemResponse("Resource conflict"));
        }
        if (method == HttpMethod.DELETE) {
            responses.addApiResponse("204", new ApiResponse().description("Resource deleted"));
        } else if (method == HttpMethod.POST && CREATE_PATHS.contains(path)) {
            responses.addApiResponse("201", new ApiResponse().description("Resource created"));
            responses.addApiResponse("409", problemResponse("Resource conflict"));
        }
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                        "application/problem+json",
                        new io.swagger.v3.oas.models.media.MediaType()));
    }
}
