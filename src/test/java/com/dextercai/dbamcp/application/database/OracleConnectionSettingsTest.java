package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dextercai.dbamcp.domain.asset.AssetId;
import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import com.dextercai.dbamcp.domain.database.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OracleConnectionSettingsTest {
    @Test
    void usesOneCanonicalEndpointAndSeparatesCredentialsFromDriverProperties() {
        OracleConnectionSettings settings = OracleConnectionSettings.from(detail(Map.of(
                "jdbcUrl", "jdbc:oracle:thin:@//oracle.internal:1521/ORCLPDB1",
                "username", "dba_mcp_ro",
                "password", "not-for-output",
                "oracle.net.CONNECT_TIMEOUT", "5000")));

        assertEquals("jdbc:oracle:thin:@//oracle.internal:1521/ORCLPDB1", settings.jdbcUrl());
        assertEquals("dba_mcp_ro", settings.username());
        assertEquals("not-for-output", settings.password());
        assertEquals("5000", settings.driverProperties().getProperty("oracle.net.CONNECT_TIMEOUT"));
        assertEquals(null, settings.driverProperties().getProperty("username"));
        assertEquals(null, settings.driverProperties().getProperty("password"));
    }

    @Test
    void rejectsCredentialsEmbeddedInJdbcUrl() {
        assertThrows(IllegalArgumentException.class, () -> OracleConnectionSettings.from(detail(Map.of(
                "jdbcUrl", "jdbc:oracle:thin:dba_mcp_ro/not-for-output@oracle.internal:1521:ORCL"))));
        assertThrows(IllegalArgumentException.class, () -> OracleConnectionSettings.from(detail(Map.of(
                "user", "dba_mcp_ro"))));
    }

    private static DatabaseDetail detail(Map<String, String> properties) {
        return new DatabaseDetail(new AssetId("oracle-1"), DatabaseType.ORACLE, "fallback.internal", 1521, "FALLBACK", "inline-password", true, false, properties);
    }
}
