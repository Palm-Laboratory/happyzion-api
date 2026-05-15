package org.happyzion.api.common.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "Happy Zion API",
        version = "v1",
        description = "Happy Zion public and admin HTTP API",
    ),
)
class OpenApiConfig
