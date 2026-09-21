package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatabaseUserCatalogTest {
    @Test
    void fixedUserQueryIsReadOnlyAndExcludesSensitiveColumns() {
        String sql = DatabaseUserCatalog.LIST_USERS_SQL.toUpperCase();

        assertDoesNotThrow(() -> new ReadOnlySqlPolicy().validate(DatabaseUserCatalog.LIST_USERS_SQL));
        assertTrue(sql.contains("ACCOUNT_STATUS"));
        assertTrue(sql.contains(" AS LOCKED"));
        assertFalse(sql.contains("PASSWORD"));
        assertFalse(sql.contains("LAST_LOGIN"));
    }
}
