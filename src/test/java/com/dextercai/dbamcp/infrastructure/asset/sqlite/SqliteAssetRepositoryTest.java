package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.dextercai.dbamcp.domain.asset.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;

class SqliteAssetRepositoryTest {
    private HikariDataSource dataSource; private SqliteAssetRepository repository;
    @BeforeEach void setUp() throws Exception {
        HikariConfig config = new HikariConfig(); config.setJdbcUrl("jdbc:sqlite:file:test" + System.nanoTime() + "?mode=memory&cache=shared"); config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config); runMigration(dataSource); repository = new SqliteAssetRepository(dataSource, new ObjectMapper());
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO asset VALUES ('host-1','HOST','host-one','Host One','test','ACTIVE','local',NULL,'{\"region\":\"cn\"}','{}',1,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
            statement.execute("INSERT INTO asset VALUES ('db-1','DATABASE_INSTANCE','db-one','DB One','test','ACTIVE','local',NULL,'{}','{}',1,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z')");
            statement.execute("INSERT INTO asset_relation VALUES ('rel-1','db-1','RUNS_ON','host-1','{}','2026-01-01T00:00:00Z')");
        }
    }
    @AfterEach void close() { dataSource.close(); }
    @Test void searchesWithTypedFiltersAndTraversesRelations() {
        assertEquals(1, repository.search(new AssetQuery(AssetType.HOST, "test", AssetStatus.ACTIVE, java.util.Map.of("region", "cn"), 0, 10)).total());
        assertEquals(1, repository.findOutgoingRelations(new AssetId("db-1"), java.util.Set.of(RelationType.RUNS_ON)).size());
    }
    private static void runMigration(DataSource source) throws Exception {
        String script = new ClassPathResource("db/migration/V1__asset_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) { for (String sql : script.split(";\\s*(?:\\r?\\n|$)")) if (!sql.isBlank()) statement.execute(sql); }
        script = new ClassPathResource("db/migration/V2__oracle_user_unlock.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) { for (String sql : script.split(";\\s*(?:\\r?\\n|$)")) if (!sql.isBlank()) statement.execute(sql); }
    }
}
