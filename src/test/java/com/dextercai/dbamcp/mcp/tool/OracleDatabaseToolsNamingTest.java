package com.dextercai.dbamcp.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class OracleDatabaseToolsNamingTest {
    @Test
    void allOracleToolsUseOracleNamespace() {
        Map<String, String> expectedNames = Map.ofEntries(
                Map.entry("testDatabaseConnection", "oracle.testDatabaseConnection"),
                Map.entry("listDatabaseUsers", "oracle.listDatabaseUsers"),
                Map.entry("unlockUser", "oracle.unlockUser"),
                Map.entry("listSchemas", "oracle.listSchemas"),
                Map.entry("listTables", "oracle.listTables"),
                Map.entry("listConstraints", "oracle.listConstraints"),
                Map.entry("listIndexes", "oracle.listIndexes"),
                Map.entry("listAlertLogEvents", "oracle.listAlertLogEvents"),
                Map.entry("listTablespaceUsage", "oracle.listTablespaceUsage"),
                Map.entry("getSessionSummary", "oracle.getSessionSummary"),
                Map.entry("listLongRunningTransactions", "oracle.listLongRunningTransactions"),
                Map.entry("listBlockingSessions", "oracle.listBlockingSessions"),
                Map.entry("describeTable", "oracle.describeTable"));

        int toolCount = 0;
        for (Method method : OracleDatabaseTools.class.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool != null) {
                toolCount++;
                assertEquals(expectedNames.get(method.getName()), tool.name());
            }
        }
        assertEquals(expectedNames.size(), toolCount);
    }

    @Test
    void alertLogPaginationParametersAreOptionalInTheToolSchema() throws NoSuchMethodException {
        Method method = OracleDatabaseTools.class.getDeclaredMethod(
                "listAlertLogEvents", String.class, Integer.class, Integer.class);

        ToolParam offset = method.getParameters()[1].getAnnotation(ToolParam.class);
        ToolParam pageSize = method.getParameters()[2].getAnnotation(ToolParam.class);

        assertFalse(offset.required());
        assertFalse(pageSize.required());
    }

    @Test
    void alertLogPaginationParametersAreNotRequiredInTheGeneratedMcpSchema() throws Exception {
        ToolCallback callback = java.util.Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(new OracleDatabaseTools(null)).build().getToolCallbacks())
                .filter(candidate -> candidate.getToolDefinition().name().equals("oracle.listAlertLogEvents"))
                .findFirst().orElse(null);

        assertNotNull(callback);
        var required = new ObjectMapper().readTree(callback.getToolDefinition().inputSchema()).path("required");
        assertEquals(1, required.size());
        assertEquals("assetId", required.get(0).asText());
    }

    @Test
    void listToolPaginationParametersAreOptionalInTheGeneratedMcpSchema() throws Exception {
        ToolCallback callback = java.util.Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(new OracleDatabaseTools(null)).build().getToolCallbacks())
                .filter(candidate -> candidate.getToolDefinition().name().equals("oracle.listTables"))
                .findFirst().orElse(null);

        assertNotNull(callback);
        var required = new ObjectMapper().readTree(callback.getToolDefinition().inputSchema()).path("required");
        assertEquals(2, required.size());
        assertEquals("assetId", required.get(0).asText());
        assertEquals("owner", required.get(1).asText());
    }
}
