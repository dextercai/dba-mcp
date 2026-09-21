package com.dextercai.dbamcp.application.asset;

import com.dextercai.dbamcp.domain.asset.Asset;
import com.dextercai.dbamcp.domain.asset.AssetRelation;
import java.util.List;
public record AssetTopology(Asset root, List<Asset> assets, List<AssetRelation> relations, boolean truncated) {
    public AssetTopology { assets = List.copyOf(assets); relations = List.copyOf(relations); }
}
