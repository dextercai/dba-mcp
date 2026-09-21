package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import com.dextercai.dbamcp.config.DbaProperties;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Minimal versioned migration runner until the store gains a full migration workflow. */
@Component
class AssetSchemaInitializer implements ApplicationRunner {
    private final DataSource dataSource; private final DbaProperties properties;
    AssetSchemaInitializer(DataSource dataSource, DbaProperties properties) { this.dataSource = dataSource; this.properties = properties; }
    @Override public void run(ApplicationArguments args) throws Exception {
        if (properties.assets().sqlite().readOnly()) return;
        String script = new ClassPathResource("db/migration/V1__asset_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : script.split(";\\s*(?:\\r?\\n|$)")) if (!sql.isBlank()) statement.execute(sql);
        }
    }
}
