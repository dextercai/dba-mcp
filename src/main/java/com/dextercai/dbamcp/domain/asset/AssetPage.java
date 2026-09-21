package com.dextercai.dbamcp.domain.asset;
import java.util.List;
public record AssetPage(List<Asset> items, long total) { public AssetPage { items = List.copyOf(items); } }
