package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TablespaceCatalogTest {
    @Test
    void fixedTablespaceQueryIsReadOnlyAndExposesOnlyRawSourceFields() {
        String sql = TablespaceCatalog.LIST_USAGE_SQL.toUpperCase();

        assertDoesNotThrow(() -> new ReadOnlySqlPolicy().validate(TablespaceCatalog.LIST_USAGE_SQL));
        assertTrue(sql.contains("DBA_DATA_FILES"));
        assertTrue(sql.contains("DBA_TEMP_FILES"));
        assertTrue(sql.contains("V$TEMP_SPACE_HEADER"));
        assertTrue(sql.contains("DBA_TABLESPACE_USAGE_METRICS"));
        assertTrue(sql.contains("BYTES_FREE"));
        assertTrue(sql.contains("USED_PERCENT"));
        assertTrue(!sql.contains("SUM("));
        assertTrue(!sql.contains("ROUND("));
    }
}
