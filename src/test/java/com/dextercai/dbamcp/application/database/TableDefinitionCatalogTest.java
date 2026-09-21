package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TableDefinitionCatalogTest {
    @Test
    void dictionaryQueriesAreFixedReadOnlyStatements() {
        ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();

        assertDoesNotThrow(() -> policy.validate(TableDefinitionCatalog.TABLE));
        assertDoesNotThrow(() -> policy.validate(TableDefinitionCatalog.COLUMNS));
        assertDoesNotThrow(() -> policy.validate(TableDefinitionCatalog.CONSTRAINTS));
        assertDoesNotThrow(() -> policy.validate(TableDefinitionCatalog.INDEXES));
        assertTrue(TableDefinitionCatalog.COLUMNS.toUpperCase().contains("FROM DBA_TAB_COLS"));
        assertFalse(TableDefinitionCatalog.COLUMNS.toUpperCase().contains("DATA_DEFAULT"));
    }

    @Test
    void normalizesOnlyConventionalUnquotedIdentifiers() {
        assertEquals("APP_OWNER", OracleIdentifier.normalize("owner", "app_owner"));
        assertEquals("ORDER_ITEMS$2026", OracleIdentifier.normalize("tableName", " order_items$2026 "));
        assertThrows(IllegalArgumentException.class, () -> OracleIdentifier.normalize("tableName", "items; DROP TABLE x"));
        assertThrows(IllegalArgumentException.class, () -> OracleIdentifier.normalize("tableName", "\"MixedCase\""));
    }
}
