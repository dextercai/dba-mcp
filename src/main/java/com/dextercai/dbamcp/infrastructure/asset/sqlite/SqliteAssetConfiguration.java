package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import com.dextercai.dbamcp.config.DbaProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class SqliteAssetConfiguration {
    @Bean(destroyMethod = "close")
    DataSource assetDataSource(DbaProperties properties) throws Exception {
        createParentDirectory(properties.assets().sqlite());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.assets().sqlite().jdbcUrl());
        config.setMaximumPoolSize(2); config.setMinimumIdle(1); config.setPoolName("asset-store");
        config.setReadOnly(properties.assets().sqlite().readOnly());
        config.setConnectionTimeout(properties.assets().sqlite().busyTimeout().toMillis());
        HikariDataSource dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + properties.assets().sqlite().busyTimeout().toMillis());
        }
        return dataSource;
    }

    private static void createParentDirectory(DbaProperties.Sqlite sqlite) throws Exception {
        if (sqlite.readOnly() || !sqlite.jdbcUrl().startsWith("jdbc:sqlite:")) return;
        String location = sqlite.jdbcUrl().substring("jdbc:sqlite:".length());
        if (location.equals(":memory:") || location.startsWith("file:")) return;
        Path parent = Path.of(location).toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
