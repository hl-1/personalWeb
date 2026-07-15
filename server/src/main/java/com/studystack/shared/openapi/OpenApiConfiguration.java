package com.studystack.shared.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "StudyStack API",
                version = "P0",
                description = "StudyStack P0 foundation API contract"))
public class OpenApiConfiguration {}
