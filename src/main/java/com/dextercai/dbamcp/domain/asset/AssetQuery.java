package com.dextercai.dbamcp.domain.asset;

import java.util.Map;

public record AssetQuery(AssetType type, String environment, AssetStatus status, Map<String, String> labels,
                         int offset, int limit) {
    public AssetQuery {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    public static AssetQuery firstPage() { return new AssetQuery(null, null, AssetStatus.ACTIVE, Map.of(), 0, 50); }
}
