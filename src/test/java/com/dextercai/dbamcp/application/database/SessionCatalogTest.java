package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionCatalogTest {
    @Test
    void sessionDiagnosticQueriesAreFixedReadOnlyAndExcludeSensitiveContents() {
        ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();

        assertDoesNotThrow(() -> policy.validate(SessionCatalog.SESSION_SUMMARY_SQL));
        assertDoesNotThrow(() -> policy.validate(SessionCatalog.LONG_RUNNING_TRANSACTIONS_SQL));
        assertDoesNotThrow(() -> policy.validate(SessionCatalog.BLOCKING_SESSIONS_SQL));

        String sql = (SessionCatalog.SESSION_SUMMARY_SQL
                + SessionCatalog.LONG_RUNNING_TRANSACTIONS_SQL
                + SessionCatalog.BLOCKING_SESSIONS_SQL).toUpperCase();
        assertTrue(sql.contains("V$SESSION"));
        assertTrue(sql.contains("V$TRANSACTION"));
        assertFalse(sql.contains("SQL_TEXT"));
        assertFalse(sql.contains("CLIENT_IDENTIFIER"));
        assertFalse(sql.contains("OSUSER"));
        assertFalse(sql.contains("XID"));
    }
}
