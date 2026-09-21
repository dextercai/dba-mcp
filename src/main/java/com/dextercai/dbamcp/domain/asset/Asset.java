package com.dextercai.dbamcp.domain.asset;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record Asset(AssetId id, AssetType type, String code, String displayName, String environment,
                    AssetStatus status, String sourceName, String externalId,
                    Map<String, String> labels, Map<String, Object> metadata,
                    long version, Instant createdAt, Instant updatedAt) {
    public Asset {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(type, "type");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("asset code is required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("display name is required");
        status = status == null ? AssetStatus.ACTIVE : status;
        sourceName = sourceName == null || sourceName.isBlank() ? "local" : sourceName;
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        Objects.requireNonNull(createdAt, "createdAt"); Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
