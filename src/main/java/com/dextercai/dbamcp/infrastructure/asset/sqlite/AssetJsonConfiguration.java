package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Isolated Jackson 2 mapper for legacy asset-store JSON; MCP protocol uses its own Jackson 3 mapper. */
@Configuration
class AssetJsonConfiguration {
    @Bean
    ObjectMapper assetStoreObjectMapper() { return new ObjectMapper(); }
}
