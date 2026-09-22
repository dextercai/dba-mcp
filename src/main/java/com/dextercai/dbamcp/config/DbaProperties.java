package com.dextercai.dbamcp.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dba")
public record DbaProperties(Assets assets, Http http, Database database) {
    public DbaProperties {
        assets = assets == null ? new Assets(null) : assets;
        http = http == null ? new Http(null, null, null, null) : http;
        database = database == null ? new Database(null, null, null) : database;
    }
    public record Assets(Sqlite sqlite) {
        public Assets { sqlite = sqlite == null ? new Sqlite("jdbc:sqlite:./data/dba-mcp-assets.db", false, Duration.ofSeconds(5)) : sqlite; }
    }
    public record Sqlite(String jdbcUrl, boolean readOnly, Duration busyTimeout) { }
    public record Http(String apiToken, String allowedOrigins, String assetAdminUsername, String assetAdminPasswordHash) { }
    public record Database(Hikari hikari, TargetPools targetPools, UserUnlock userUnlock) {
        public Database {
            hikari = hikari == null ? new Hikari(
                    Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ZERO,
                    Duration.ofMinutes(30), Duration.ofMinutes(10)) : hikari;
            targetPools = targetPools == null ? new TargetPools(128, 1, Duration.ofMinutes(1)) : targetPools;
            userUnlock = userUnlock == null ? new UserUnlock(false) : userUnlock;
        }
    }
    /** Explicit, fail-closed runtime permission for the dedicated Oracle account-unlock operation. */
    public record UserUnlock(boolean enabled) { }
    /** Bounded cache for short-lived, asset-specific database pools. */
    public record TargetPools(int maximumCachedPools, int maximumPoolSize, Duration cacheIdleTimeout) {
        public TargetPools {
            if (maximumCachedPools < 1) throw new IllegalArgumentException("maximumCachedPools must be positive");
            if (maximumPoolSize < 1) throw new IllegalArgumentException("maximumPoolSize must be positive");
            if (cacheIdleTimeout == null || cacheIdleTimeout.isNegative() || cacheIdleTimeout.isZero()) {
                throw new IllegalArgumentException("cacheIdleTimeout must be positive");
            }
        }
    }
    public record Hikari(Duration connectionTimeout, Duration validationTimeout, Duration keepaliveTime,
                         Duration maxLifetime, Duration idleTimeout) { }
}
