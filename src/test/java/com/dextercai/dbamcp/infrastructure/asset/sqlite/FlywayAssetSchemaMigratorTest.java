package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class FlywayAssetSchemaMigratorTest {
    @Test
    void baselinesAnExistingUntrackedV1StoreAndThenAppliesV2() throws Exception {
        Path database = Files.createTempFile("dba-mcp-v1-", ".db");
        String url = "jdbc:sqlite:" + database;
        try {
            String v1 = new ClassPathResource("db/migration/V1__asset_schema.sql").getContentAsString(StandardCharsets.UTF_8);
            try (var connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                for (String sql : v1.split(";\\s*(?:\\r?\\n|$)")) if (!sql.isBlank()) statement.execute(sql);
            }

            Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("1").load().migrate();

            try (var connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
                var column = statement.executeQuery("PRAGMA table_info(database_detail)");
                boolean hasUnlockFlag = false;
                while (column.next()) hasUnlockFlag |= "user_unlock_enabled".equals(column.getString("name"));
                assertTrue(hasUnlockFlag);
                var history = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank");
                assertTrue(history.next()); assertEquals("1", history.getString(1));
                assertTrue(history.next()); assertEquals("2", history.getString(1));
            }
        } finally {
            Files.deleteIfExists(database);
        }
    }
}
