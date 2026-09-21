package com.dextercai.dbamcp.api;

import com.dextercai.dbamcp.application.asset.AssetManagementService;
import com.dextercai.dbamcp.application.asset.AssetManagementService.*;
import com.dextercai.dbamcp.application.asset.AssetService;
import com.dextercai.dbamcp.application.asset.AssetNotFoundException;
import com.dextercai.dbamcp.domain.asset.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Basic-authenticated REST surface for maintaining the registered asset inventory. */
@RestController
@Profile("http")
@RequestMapping("/api/v1")
@Tag(name = "Asset inventory administration")
@SecurityRequirement(name = "basicAuth")
public class AssetManagementController {
    private final AssetService assets; private final AssetManagementService management;
    public AssetManagementController(AssetService assets, AssetManagementService management) { this.assets = assets; this.management = management; }

    @GetMapping("/assets") @Operation(summary = "List registered assets")
    public AssetPage list(@RequestParam(required = false) AssetType type, @RequestParam(required = false) String environment, @RequestParam(required = false) AssetStatus status, @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "50") int limit) { return assets.list(new AssetQuery(type, environment, status, Map.of(), offset, limit)); }
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Create an asset and optional typed detail")
    public ManagedAsset create(@RequestBody AssetDraft request) { return management.create(request); }
    @GetMapping("/assets/{assetId}") @Operation(summary = "Get asset and sanitized typed detail")
    public ManagedAsset get(@PathVariable String assetId) { return management.get(new AssetId(assetId)); }
    @PatchMapping("/assets/{assetId}") @Operation(summary = "Update an asset using optimistic locking")
    public ManagedAsset update(@PathVariable String assetId, @RequestParam long version, @RequestBody AssetDraft request) { return management.update(new AssetId(assetId), version, request); }
    @DeleteMapping("/assets/{assetId}") @Operation(summary = "Retire an asset without physically deleting it") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@PathVariable String assetId, @RequestParam long version) { management.retire(new AssetId(assetId), version); }
    @PostMapping("/assets/{assetId}/restore") @Operation(summary = "Restore a retired asset")
    public ManagedAsset restore(@PathVariable String assetId, @RequestParam long version) { return management.restore(new AssetId(assetId), version); }
    @GetMapping("/assets/{assetId}/relations") @Operation(summary = "List related assets")
    public List<AssetRelation> related(@PathVariable String assetId, @RequestParam(defaultValue = "false") boolean incoming, @RequestParam(required = false) Set<RelationType> types) { return assets.related(new AssetId(assetId), types == null ? Set.of() : types, incoming); }
    @PostMapping("/assets/{assetId}/relations") @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Create an outgoing asset relation")
    public AssetRelation createRelation(@PathVariable String assetId, @RequestBody RelationInput request) { return management.createRelation(new RelationDraft(new AssetId(assetId), request.type(), new AssetId(request.targetAssetId()), request.attributes())); }
    @PatchMapping("/asset-relations/{relationId}") @Operation(summary = "Update relation attributes")
    public AssetRelation updateRelation(@PathVariable String relationId, @RequestBody Map<String, Object> attributes) { return management.updateRelation(relationId, attributes); }
    @DeleteMapping("/asset-relations/{relationId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Delete an asset relation")
    public void deleteRelation(@PathVariable String relationId) { management.deleteRelation(relationId); }
    @Schema(description = "The source asset is supplied in the request path; source, target, and type are immutable after creation.")
    public record RelationInput(RelationType type, String targetAssetId, Map<String, Object> attributes) { }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class}) @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ApiResponse(responseCode = "400", description = "Invalid request") public Map<String, String> badRequest(RuntimeException error) { return Map.of("code", "INVALID_REQUEST", "message", error.getMessage()); }
    @ExceptionHandler(AssetNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> assetNotFound(AssetNotFoundException error) { return Map.of("code", "ASSET_NOT_FOUND", "message", "asset not found"); }
    @ExceptionHandler(AssetVersionConflictException.class) @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> versionConflict(AssetVersionConflictException error) { return Map.of("code", "VERSION_CONFLICT", "message", error.getMessage()); }
}
