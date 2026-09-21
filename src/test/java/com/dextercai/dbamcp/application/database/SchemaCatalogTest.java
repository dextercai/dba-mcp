package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchemaCatalogTest {
    @Test
    void schemaInventoryQueriesAreFixedReadOnlyStatements() {
        ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();

        assertDoesNotThrow(() -> policy.validate(SchemaCatalog.LIST_SCHEMAS));
        assertDoesNotThrow(() -> policy.validate(SchemaCatalog.LIST_TABLES));
        assertTrue(SchemaCatalog.LIST_TABLES.contains("owner = ?"));
    }
}
