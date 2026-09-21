package com.dextercai.dbamcp.application.asset;

import com.dextercai.dbamcp.domain.asset.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AssetService {
    private static final int MAX_TOPOLOGY_NODES = 200;
    private final AssetRepository repository;
    public AssetService(AssetRepository repository) { this.repository = repository; }
    public AssetPage list(AssetQuery query) { return repository.search(query); }
    public Asset get(AssetId id) { return repository.findById(id).orElseThrow(() -> new AssetNotFoundException("asset not found: " + id)); }
    public List<AssetRelation> related(AssetId id, Set<RelationType> types, boolean incoming) {
        get(id); return incoming ? repository.findIncomingRelations(id, types) : repository.findOutgoingRelations(id, types);
    }
    public AssetTopology topology(AssetId rootId, int depth) {
        if (depth < 0 || depth > 5) throw new IllegalArgumentException("depth must be between 0 and 5");
        Asset root = get(rootId); Map<AssetId, Asset> assets = new LinkedHashMap<>(); assets.put(rootId, root);
        List<AssetRelation> relations = new ArrayList<>(); Set<AssetId> frontier = Set.of(rootId); boolean truncated = false;
        for (int currentDepth = 0; currentDepth < depth && !frontier.isEmpty(); currentDepth++) {
            Set<AssetId> next = new LinkedHashSet<>();
            for (AssetId current : frontier) {
                List<AssetRelation> edges = new ArrayList<>(); edges.addAll(repository.findOutgoingRelations(current, Set.of())); edges.addAll(repository.findIncomingRelations(current, Set.of()));
                for (AssetRelation edge : edges) {
                    AssetId neighbor = edge.sourceAssetId().equals(current) ? edge.targetAssetId() : edge.sourceAssetId();
                    if (assets.size() >= MAX_TOPOLOGY_NODES && !assets.containsKey(neighbor)) { truncated = true; continue; }
                    relations.add(edge);
                    if (!assets.containsKey(neighbor)) { repository.findById(neighbor).ifPresent(asset -> { assets.put(neighbor, asset); next.add(neighbor); }); }
                }
            }
            frontier = next;
        }
        return new AssetTopology(root, new ArrayList<>(assets.values()), relations.stream().distinct().toList(), truncated);
    }
}
