package com.dextercai.dbamcp.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("http")
class OpenApiConfiguration {
    @Bean OpenAPI assetManagementOpenApi() {
        return new OpenAPI().info(new Info().title("DBA MCP Asset Inventory API").version("v1").description("Basic-authenticated API for the registered SQLite asset inventory. Password fields are write-only and never returned."))
                .components(new Components().addSecuritySchemes("basicAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}
