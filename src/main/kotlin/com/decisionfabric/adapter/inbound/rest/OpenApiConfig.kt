package com.decisionfabric.adapter.inbound.rest

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Decision Fabric AI")
                .version("1.0")
                .description(
                    """
                    REST API for managing DMN decision rules and executing decisions.
                    
                    **Authentication**: obtain a Bearer JWT from your OIDC provider and paste it using the
                    **Authorize** button (top-right). Roles required per endpoint are documented in each
                    operation's description.
                    """.trimIndent()
                )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the access_token from your OIDC provider.")
            )
        )
}
