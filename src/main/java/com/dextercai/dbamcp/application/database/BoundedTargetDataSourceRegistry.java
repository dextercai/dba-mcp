package com.dextercai.dbamcp.application.database;

import com.dextercai.dbamcp.config.DbaProperties.TargetPools;
import com.dextercai.dbamcp.domain.asset.AssetId;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Bounds the number of target-specific pools independently from inventory size.
 * A pool is only evicted while no request is borrowing it, and is always closed
 * before it leaves the registry.
 */
final class BoundedTargetDataSourceRegistry implements AutoCloseable {
    private final TargetPools settings;
    private final Function<AssetId, HikariDataSource> factory;
    private final Clock clock;
    private final ScheduledExecutorService reaper;
    private final Map<AssetId, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    BoundedTargetDataSourceRegistry(TargetPools settings, Function<AssetId, HikariDataSource> factory) {
        this(settings, factory, Clock.systemUTC());
    }

    BoundedTargetDataSourceRegistry(TargetPools settings, Function<AssetId, HikariDataSource> factory, Clock clock) {
        this.settings = settings;
        this.factory = factory;
        this.clock = clock;
        this.reaper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "database-target-pool-reaper");
            thread.setDaemon(true);
            return thread;
        });
        long intervalMillis = settings.cacheIdleTimeout().toMillis();
        reaper.scheduleWithFixedDelay(this::evictIdle, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    <T> T withDataSource(AssetId id, SqlWork<T> work) throws SQLException {
        Entry entry = borrow(id);
        try {
            return work.execute(entry.dataSource);
        } finally {
            release(id, entry);
        }
    }

    private synchronized Entry borrow(AssetId id) {
        evictIdle();
        Entry entry = entries.get(id);
        if (entry == null) {
            ensureCapacity();
            entry = new Entry(factory.apply(id), clock.millis());
            entries.put(id, entry);
        }
        entry.borrowers++;
        entry.lastUsedAt = clock.millis();
        return entry;
    }

    private synchronized void release(AssetId id, Entry entry) {
        entry.borrowers--;
        entry.lastUsedAt = clock.millis();
        // Touch the LRU entry only if it has not been replaced (which should not
        // happen while borrowed, but keeps the invariant explicit).
        if (entries.get(id) == entry) entries.get(id);
    }

    private synchronized void evictIdle() {
        long cutoff = clock.millis() - settings.cacheIdleTimeout().toMillis();
        Iterator<Map.Entry<AssetId, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (entry.borrowers == 0 && entry.lastUsedAt <= cutoff) {
                entry.dataSource.close();
                iterator.remove();
            }
        }
    }

    private void ensureCapacity() {
        while (entries.size() >= settings.maximumCachedPools()) {
            Iterator<Map.Entry<AssetId, Entry>> iterator = entries.entrySet().iterator();
            boolean evicted = false;
            while (iterator.hasNext()) {
                Entry entry = iterator.next().getValue();
                if (entry.borrowers == 0) {
                    entry.dataSource.close();
                    iterator.remove();
                    evicted = true;
                    break;
                }
            }
            if (!evicted) throw new DatabaseRegistryCapacityException(settings.maximumCachedPools());
        }
    }

    @Override public synchronized void close() {
        reaper.shutdownNow();
        entries.values().forEach(entry -> entry.dataSource.close());
        entries.clear();
    }

    @FunctionalInterface interface SqlWork<T> { T execute(HikariDataSource dataSource) throws SQLException; }
    private static final class Entry {
        private final HikariDataSource dataSource;
        private int borrowers;
        private long lastUsedAt;
        private Entry(HikariDataSource dataSource, long lastUsedAt) { this.dataSource = dataSource; this.lastUsedAt = lastUsedAt; }
    }
    static final class DatabaseRegistryCapacityException extends RuntimeException {
        DatabaseRegistryCapacityException(int maximumCachedPools) { super("database target pool capacity reached: " + maximumCachedPools); }
    }
}
