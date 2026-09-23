package com.dextercai.dbamcp.application.database;

/** Validated one-based page request for bounded Oracle list tools. */
record OraclePageRequest(int pageNum, int pageSize) {
    static final int DEFAULT_PAGE_NUM = 1;
    static final int DEFAULT_PAGE_SIZE = 100;
    static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_PAGE_NUM = 10_000;

    static OraclePageRequest from(Integer pageNum, Integer pageSize) {
        int normalizedPageNum = pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
        int normalizedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (normalizedPageNum < 1 || normalizedPageNum > MAX_PAGE_NUM) {
            throw new IllegalArgumentException("pageNum must be between 1 and " + MAX_PAGE_NUM);
        }
        if (normalizedPageSize < 1 || normalizedPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        return new OraclePageRequest(normalizedPageNum, normalizedPageSize);
    }

    int offset() {
        return Math.multiplyExact(pageNum - 1, pageSize);
    }

    int fetchSizeWithProbe() {
        return Math.addExact(pageSize, 1);
    }
}
