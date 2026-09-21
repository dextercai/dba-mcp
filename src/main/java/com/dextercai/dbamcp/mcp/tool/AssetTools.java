package com.dextercai.dbamcp.mcp.tool;

import com.dextercai.dbamcp.application.asset.AssetService;
import com.dextercai.dbamcp.domain.asset.*;
import java.util.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Initial safe MCP surface: inventory only; no raw targets, secrets, SQL, or shell input. */
@Component
public class AssetTools {
    private final AssetService assets;
    public AssetTools(AssetService assets) { this.assets = assets; }
    @Tool(description = "List registered DBA assets. Optional type, environment, and status filters are enums or inventory fields only.")
    public AssetPage listAssets(@ToolParam(required = false) String type,
                                @ToolParam(required = false) String environment,
                                @ToolParam(required = false) String status,
                                @ToolParam(required = false) Integer offset,
                                @ToolParam(required = false) Integer limit) {
        return assets.list(new AssetQuery(enumOrNull(type, AssetType.class), blankToNull(environment), enumOrDefault(status, AssetStatus.class, AssetStatus.ACTIVE), Map.of(), offset == null ? 0 : offset, limit == null ? 50 : limit));
    }
    @Tool(description = "Get one registered asset by its stable asset ID. Does not disclose credentials or raw secret values.")
    public Asset getAsset(String assetId) { return assets.get(new AssetId(assetId)); }
    @Tool(description = "Return the bounded asset graph around a registered asset. Depth is limited to five hops.")
    public Object getAssetTopology(String assetId, @ToolParam(required = false) Integer depth) { return assets.topology(new AssetId(assetId), depth == null ? 1 : depth); }
    @Tool(description = "List explicitly registered related assets. Relation type values are fixed server enums.")
    public List<AssetRelation> listRelatedAssets(String assetId,
                                                  @ToolParam(required = false) List<String> relationTypes,
                                                  @ToolParam(required = false) Boolean incoming) {
        Set<RelationType> types = relationTypes == null ? Set.of() : relationTypes.stream().map(value -> RelationType.valueOf(value.toUpperCase(Locale.ROOT))).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return assets.related(new AssetId(assetId), types, Boolean.TRUE.equals(incoming));
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static <T extends Enum<T>> T enumOrNull(String value, Class<T> type) { return value == null || value.isBlank() ? null : Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
    private static <T extends Enum<T>> T enumOrDefault(String value, Class<T> type, T fallback) { T parsed = enumOrNull(value, type); return parsed == null ? fallback : parsed; }
}
