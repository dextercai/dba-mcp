package com.dextercai.dbamcp.application.asset;

import com.dextercai.dbamcp.domain.asset.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Write-side service for the controlled SQLite asset inventory. */
@Service
public class AssetManagementService {
    private final AssetRepository repository;
    public AssetManagementService(AssetRepository repository) { this.repository = repository; }

    public ManagedAsset create(AssetDraft draft) {
        validateDraft(draft);
        Instant now = Instant.now();
        Asset asset = new Asset(new AssetId(UUID.randomUUID().toString()), draft.type(), draft.code(), draft.displayName(), draft.environment(),
                draft.status() == null ? AssetStatus.ACTIVE : draft.status(), draft.sourceName(), draft.externalId(), draft.labels(), draft.metadata(), 1, now, now);
        return managed(repository.create(asset, draft.detail()));
    }
    public ManagedAsset get(AssetId id) { return managed(repository.findById(id).orElseThrow(() -> new AssetNotFoundException("asset not found: " + id))); }
    public ManagedAsset update(AssetId id, long version, AssetDraft draft) {
        Asset existing = repository.findById(id).orElseThrow(() -> new AssetNotFoundException("asset not found: " + id));
        if (existing.type() != draft.type()) throw new IllegalArgumentException("asset type cannot be changed");
        validateDraft(draft);
        Asset requested = new Asset(id, existing.type(), draft.code(), draft.displayName(), draft.environment(), draft.status() == null ? existing.status() : draft.status(),
                draft.sourceName() == null ? existing.sourceName() : draft.sourceName(), draft.externalId(), draft.labels(), draft.metadata(), existing.version(), existing.createdAt(), existing.updatedAt());
        return managed(repository.update(requested, version, draft.detail()));
    }
    public ManagedAsset retire(AssetId id, long version) { return managed(repository.updateStatus(id, AssetStatus.RETIRED, version)); }
    public ManagedAsset restore(AssetId id, long version) { return managed(repository.updateStatus(id, AssetStatus.ACTIVE, version)); }
    public AssetRelation createRelation(RelationDraft draft) {
        if (draft.sourceAssetId().equals(draft.targetAssetId())) throw new IllegalArgumentException("self relation is not permitted");
        repository.findById(draft.sourceAssetId()).orElseThrow(() -> new AssetNotFoundException("source asset not found"));
        repository.findById(draft.targetAssetId()).orElseThrow(() -> new AssetNotFoundException("target asset not found"));
        return repository.createRelation(new AssetRelation(UUID.randomUUID().toString(), draft.sourceAssetId(), draft.type(), draft.targetAssetId(), draft.attributes(), Instant.now()));
    }
    public AssetRelation updateRelation(String id, Map<String, Object> attributes) { return repository.updateRelationAttributes(id, attributes == null ? Map.of() : attributes); }
    public void deleteRelation(String id) { repository.deleteRelation(id); }
    private ManagedAsset managed(Asset asset) { return new ManagedAsset(asset, repository.findDetail(asset.id(), asset.type()).orElse(Map.of())); }
    private static void validateDraft(AssetDraft draft) {
        Objects.requireNonNull(draft, "request is required"); Objects.requireNonNull(draft.type(), "type is required");
        if (draft.code() == null || draft.code().isBlank() || draft.displayName() == null || draft.displayName().isBlank()) throw new IllegalArgumentException("code and displayName are required");
        Map<String, Object> detail = draft.detail() == null ? Map.of() : draft.detail();
        if ((draft.type() == AssetType.DATABASE_INSTANCE || draft.type() == AssetType.DATABASE_SERVICE) && !detail.isEmpty() && (!detail.containsKey("database_type") || !detail.containsKey("host") || !detail.containsKey("port"))) throw new IllegalArgumentException("database detail requires database_type, host, and port");
    }
    public record AssetDraft(AssetType type, String code, String displayName, String environment, AssetStatus status, String sourceName, String externalId, Map<String, String> labels, Map<String, Object> metadata, Map<String, Object> detail) {
        public AssetDraft { labels = labels == null ? Map.of() : Map.copyOf(labels); metadata = metadata == null ? Map.of() : Map.copyOf(metadata); detail = detail == null ? Map.of() : Map.copyOf(detail); }
    }
    public record RelationDraft(AssetId sourceAssetId, RelationType type, AssetId targetAssetId, Map<String, Object> attributes) { public RelationDraft { attributes = attributes == null ? Map.of() : Map.copyOf(attributes); } }
    public record ManagedAsset(Asset asset, Map<String, Object> detail) { public ManagedAsset { detail = Collections.unmodifiableMap(new LinkedHashMap<>(detail)); } }
}
