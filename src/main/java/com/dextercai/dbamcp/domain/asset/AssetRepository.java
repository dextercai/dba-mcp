package com.dextercai.dbamcp.domain.asset;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import java.util.Map;

public interface AssetRepository {
    Optional<Asset> findById(AssetId id);
    Optional<Asset> findByCode(String code);
    AssetPage search(AssetQuery query);
    List<AssetRelation> findOutgoingRelations(AssetId sourceId, Set<RelationType> relationTypes);
    List<AssetRelation> findIncomingRelations(AssetId targetId, Set<RelationType> relationTypes);
    Optional<DatabaseDetail> findDatabaseDetail(AssetId assetId);
    Optional<Map<String, Object>> findDetail(AssetId assetId, AssetType type);
    Asset create(Asset asset, Map<String, Object> detail);
    Asset update(Asset asset, long expectedVersion, Map<String, Object> detail);
    Asset updateStatus(AssetId id, AssetStatus status, long expectedVersion);
    AssetRelation createRelation(AssetRelation relation);
    AssetRelation updateRelationAttributes(String relationId, Map<String, Object> attributes);
    void deleteRelation(String relationId);
}
