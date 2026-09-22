package com.dextercai.dbamcp.domain.database;

import com.dextercai.dbamcp.domain.asset.AssetId;
import java.util.Map;
public record DatabaseDetail(AssetId assetId, DatabaseType type, String host, int port, String serviceName,
                             String credentialRef, boolean readOnly, boolean userUnlockEnabled,
                             Map<String, String> connectionProperties) {
    public DatabaseDetail { connectionProperties = connectionProperties == null ? Map.of() : Map.copyOf(connectionProperties); }
}
