package com.dextercai.dbamcp.mcp.tool;
import com.dextercai.dbamcp.application.database.OracleDatabaseService;
import com.dextercai.dbamcp.application.database.OracleUserUnlockResult;
import com.dextercai.dbamcp.application.database.QueryResult;
import com.dextercai.dbamcp.application.database.TableDefinition;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
@Component
public class OracleDatabaseTools {
    private final OracleDatabaseService databases; public OracleDatabaseTools(OracleDatabaseService databases) { this.databases = databases; }
    @Tool(name = "oracle.testDatabaseConnection", description = "Test a registered Oracle database connection using its stable asset ID.") public Map<String, String> testDatabaseConnection(String assetId) { return databases.testConnection(assetId); }
    @Tool(name = "oracle.listDatabaseUsers", description = "List Oracle database users from DBA_USERS on a registered asset. Includes explicit locked status and non-sensitive account metadata; results are bounded.")
    public QueryResult listDatabaseUsers(String assetId) { return databases.listUsers(assetId); }
    @Tool(name = "oracle.unlockUser", description = "Unlock one registered Oracle account only when the runtime and a dedicated non-read-only target asset explicitly enable it. The account must be a conventional unquoted identifier and is denied if it has DBA-like roles, broad system privileges, or password-file administrative privileges.")
    public OracleUserUnlockResult unlockUser(String assetId, String username) { return databases.unlockUser(assetId, username); }
    @Tool(name = "oracle.listSchemas", description = "List Oracle schemas from DBA_USERS on a registered asset. Includes non-sensitive account metadata; results are bounded.")
    public QueryResult listSchemas(String assetId) { return databases.listSchemas(assetId); }
    @Tool(name = "oracle.listTables", description = "List tables in one Oracle schema on a registered asset. Owner must be an unquoted Oracle identifier; results are bounded.")
    public QueryResult listTables(String assetId, String owner) { return databases.listTables(assetId, owner); }
    @Tool(name = "oracle.listConstraints", description = "List constraints across one Oracle schema on a registered asset. Use describeTable for one table; owner must be an unquoted Oracle identifier; results are bounded.")
    public QueryResult listConstraints(String assetId, String owner) { return databases.listConstraints(assetId, owner); }
    @Tool(name = "oracle.listIndexes", description = "List indexes across one Oracle schema on a registered asset. Use describeTable for one table; owner must be an unquoted Oracle identifier; results are bounded.")
    public QueryResult listIndexes(String assetId, String owner) { return databases.listIndexes(assetId, owner); }
    @Tool(name = "oracle.listAlertLogEvents", description = "List one offset-based page of recent Oracle ADR alert events for the current container on a registered asset. offset defaults to 0; pageSize defaults to 100 and is 1-500. Raw message text is returned only when the server-side sensitive-message setting is explicitly enabled.")
    public QueryResult listAlertLogEvents(String assetId,
            @ToolParam(required = false, description = "Zero-based number of newer events to skip; defaults to 0.") Integer offset,
            @ToolParam(required = false, description = "Number of events to return, from 1 to 500; defaults to 100.") Integer pageSize) {
        return databases.listAlertLogEvents(assetId, offset, pageSize);
    }
    @Tool(name = "oracle.listTablespaceUsage", description = "List raw permanent and temporary Oracle tablespace capacity fields from fixed data dictionary views. Does not calculate totals, free space, or usage percentages; results are bounded.")
    public QueryResult listTablespaceUsage(String assetId) { return databases.listTablespaceUsage(assetId); }
    @Tool(name = "oracle.getSessionSummary", description = "Return a bounded aggregate of Oracle user sessions by database user and status. Does not expose client identifiers, operating-system users, or SQL text.")
    public QueryResult getSessionSummary(String assetId) { return databases.getSessionSummary(assetId); }
    @Tool(name = "oracle.listLongRunningTransactions", description = "List bounded metadata for open Oracle user transactions, without transaction identifiers or SQL text.")
    public QueryResult listLongRunningTransactions(String assetId) { return databases.listLongRunningTransactions(assetId); }
    @Tool(name = "oracle.listBlockingSessions", description = "List bounded Oracle session waiter and blocker metadata from the local instance, without SQL text or client identifiers.")
    public QueryResult listBlockingSessions(String assetId) { return databases.listBlockingSessions(assetId); }
    @Tool(name = "oracle.describeTable", description = "Describe one conventional Oracle table from fixed data dictionary queries. Returns table attributes, columns, constraints, and indexes. Owner and table name are required unquoted identifiers.")
    public TableDefinition describeTable(String assetId, String owner, String tableName) { return databases.describeTable(assetId, owner, tableName); }
}
