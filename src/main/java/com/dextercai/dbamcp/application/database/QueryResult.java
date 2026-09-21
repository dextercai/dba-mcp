package com.dextercai.dbamcp.application.database;
import java.util.List;
import java.util.Map;
public record QueryResult(String executionId, List<String> columns, List<Map<String, Object>> rows, int rowCount, boolean truncated, long elapsedMs) { public QueryResult { columns = List.copyOf(columns); rows = List.copyOf(rows); } }
