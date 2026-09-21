package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dextercai.dbamcp.config.DbaProperties.TargetPools;
import com.dextercai.dbamcp.domain.asset.AssetId;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoundedTargetDataSourceRegistryTest {
    @Test
    void evictsAndClosesLeastRecentlyUsedIdlePoolAtCapacity() throws Exception {
        Map<AssetId, HikariDataSource> created = new HashMap<>();
        BoundedTargetDataSourceRegistry registry = registry(1, created);
        AssetId first = new AssetId("oracle-1");
        AssetId second = new AssetId("oracle-2");

        registry.withDataSource(first, ignored -> null);
        registry.withDataSource(second, ignored -> null);

        org.junit.jupiter.api.Assertions.assertTrue(created.get(first).isClosed());
        registry.close();
        org.junit.jupiter.api.Assertions.assertTrue(created.get(second).isClosed());
    }

    @Test
    void neverEvictsPoolBorrowedByAnInFlightRequest() {
        Map<AssetId, HikariDataSource> created = new HashMap<>();
        BoundedTargetDataSourceRegistry registry = registry(1, created);
        AssetId first = new AssetId("oracle-1");
        AssetId second = new AssetId("oracle-2");

        assertThrows(BoundedTargetDataSourceRegistry.DatabaseRegistryCapacityException.class,
                () -> registry.withDataSource(first, ignored -> registry.withDataSource(second, alsoIgnored -> null)));

        org.junit.jupiter.api.Assertions.assertFalse(created.get(first).isClosed());
        registry.close();
        org.junit.jupiter.api.Assertions.assertTrue(created.get(first).isClosed());
    }

    private static BoundedTargetDataSourceRegistry registry(int capacity, Map<AssetId, HikariDataSource> created) {
        return new BoundedTargetDataSourceRegistry(new TargetPools(capacity, 1, Duration.ofHours(1)), id -> {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:file:" + id.value() + System.nanoTime() + "?mode=memory&cache=shared");
            config.setMaximumPoolSize(1);
            HikariDataSource dataSource = new HikariDataSource(config);
            created.put(id, dataSource);
            return dataSource;
        });
    }
}
