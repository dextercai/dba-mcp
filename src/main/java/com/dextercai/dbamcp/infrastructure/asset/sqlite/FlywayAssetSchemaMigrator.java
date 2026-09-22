package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import com.dextercai.dbamcp.config.DbaProperties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Applies tracked SQLite schema migrations. Existing production V1 stores are
 * deliberately baselined at V1 so V1 is not replayed before V2 is installed.
 */
@Component
class FlywayAssetSchemaMigrator implements ApplicationRunner {
    private final DataSource dataSource;
    private final DbaProperties properties;

    FlywayAssetSchemaMigrator(DataSource dataSource, DbaProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override public void run(ApplicationArguments args) {
        if (properties.assets().sqlite().readOnly()) return;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }
}
