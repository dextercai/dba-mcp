package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OracleMetadataCatalogTest {
    @Test
    void inventoryQueriesAreFixedReadOnlyStatements() {
        ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();

        assertDoesNotThrow(() -> policy.validate(OracleMetadataCatalog.LIST_CONSTRAINTS_SQL));
        assertDoesNotThrow(() -> policy.validate(OracleMetadataCatalog.LIST_INDEXES_SQL));
        assertDoesNotThrow(() -> policy.validate(OracleAlertLogCatalog.LIST_EVENTS_METADATA_SQL));
        assertDoesNotThrow(() -> policy.validate(OracleAlertLogCatalog.LIST_EVENTS_WITH_MESSAGE_SQL));
        assertTrue(OracleMetadataCatalog.LIST_CONSTRAINTS_SQL.contains("WHERE c.owner = ?"));
        assertTrue(OracleMetadataCatalog.LIST_INDEXES_SQL.contains("WHERE i.table_owner = ?"));
        assertTrue(OracleAlertLogCatalog.LIST_EVENTS_METADATA_SQL.contains("ROWNUM <= ?"));
        assertTrue(OracleAlertLogCatalog.LIST_EVENTS_METADATA_SQL.contains("page_row_number > ?"));
    }

    @Test
    void alertLogMessageTextIsAbsentUnlessTheSensitiveQueryIsSelected() {
        assertTrue(!OracleAlertLogCatalog.LIST_EVENTS_METADATA_SQL.toUpperCase().contains("MESSAGE_TEXT"));
        assertTrue(OracleAlertLogCatalog.LIST_EVENTS_WITH_MESSAGE_SQL.toUpperCase().contains("MESSAGE_TEXT"));
    }
}
