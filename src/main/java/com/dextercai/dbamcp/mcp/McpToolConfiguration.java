package com.dextercai.dbamcp.mcp;

import com.dextercai.dbamcp.mcp.tool.AssetTools;
import com.dextercai.dbamcp.mcp.tool.OracleDatabaseTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class McpToolConfiguration {
    @Bean
    ToolCallbackProvider assetToolCallbacks(AssetTools assetTools, OracleDatabaseTools oracleDatabaseTools) {
        return MethodToolCallbackProvider.builder().toolObjects(assetTools, oracleDatabaseTools).build();
    }
}
