package com.dextercai.dbamcp.domain.asset;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AssetRelation(String id, AssetId sourceAssetId, RelationType type, AssetId targetAssetId,
                            Map<String, Object> attributes, Instant createdAt) {
    public AssetRelation {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(sourceAssetId, "sourceAssetId");
        Objects.requireNonNull(type, "type"); Objects.requireNonNull(targetAssetId, "targetAssetId");
        if (sourceAssetId.equals(targetAssetId)) throw new IllegalArgumentException("self relation is not permitted");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
