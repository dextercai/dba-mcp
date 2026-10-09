package com.dextercai.dbamcp.application.database;
import java.util.List;
import java.util.Map;
public record QueryResult(String executionId, List<String> columns, List<Map<String, Object>> rows, int rowCount,
                          boolean truncated, boolean hasNextPage, Integer nextPageNum, Integer nextOffset, long elapsedMs) {
    public QueryResult { columns = List.copyOf(columns); rows = List.copyOf(rows); }

    public QueryResult(String executionId, List<String> columns, List<Map<String, Object>> rows, int rowCount,
                       boolean truncated, long elapsedMs) {
        this(executionId, columns, rows, rowCount, truncated, false, null, null, elapsedMs);
    }

    QueryResult forPage(OraclePageRequest page) {
        return new QueryResult(executionId, columns, rows, rowCount, truncated, hasNextPage,
                hasNextPage ? page.pageNum() + 1 : null, null, elapsedMs);
    }

    QueryResult forOffset(int offset, int pageSize) {
        return new QueryResult(executionId, columns, rows, rowCount, truncated, hasNextPage,
                null, hasNextPage ? Math.addExact(offset, pageSize) : null, elapsedMs);
    }
}
