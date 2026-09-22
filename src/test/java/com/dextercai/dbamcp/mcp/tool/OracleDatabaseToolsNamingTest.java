package com.dextercai.dbamcp.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class OracleDatabaseToolsNamingTest {
    @Test
    void allOracleToolsUseOracleNamespace() {
        Map<String, String> expectedNames = Map.of(
                "testDatabaseConnection", "oracle.testDatabaseConnection",
                "listDatabaseUsers", "oracle.listDatabaseUsers",
                "unlockUser", "oracle.unlockUser",
                "listSchemas", "oracle.listSchemas",
                "listTables", "oracle.listTables",
                "listTablespaceUsage", "oracle.listTablespaceUsage",
                "getSessionSummary", "oracle.getSessionSummary",
                "listLongRunningTransactions", "oracle.listLongRunningTransactions",
                "listBlockingSessions", "oracle.listBlockingSessions",
                "describeTable", "oracle.describeTable");

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
}
