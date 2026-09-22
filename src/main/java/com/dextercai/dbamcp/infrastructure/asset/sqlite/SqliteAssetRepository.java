package com.dextercai.dbamcp.infrastructure.asset.sqlite;

import com.dextercai.dbamcp.domain.asset.*;
import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import com.dextercai.dbamcp.domain.database.DatabaseType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

/** JDBC implementation. Domain code depends only on {@link AssetRepository}. */
@Repository
public class SqliteAssetRepository implements AssetRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public SqliteAssetRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override public Optional<Asset> findById(AssetId id) {
        return one("SELECT * FROM asset WHERE id = ?", id.value());
    }
    @Override public Optional<Asset> findByCode(String code) {
        return one("SELECT * FROM asset WHERE asset_code = ?", code);
    }
    @Override public AssetPage search(AssetQuery query) {
        List<String> where = new ArrayList<>(); List<Object> params = new ArrayList<>();
        if (query.type() != null) { where.add("asset_type = ?"); params.add(query.type().name()); }
        if (query.environment() != null && !query.environment().isBlank()) { where.add("environment = ?"); params.add(query.environment()); }
        if (query.status() != null) { where.add("status = ?"); params.add(query.status().name()); }
        query.labels().forEach((key, value) -> { where.add("json_extract(labels_json, ?) = ?"); params.add("$." + key); params.add(value); });
        String clause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
        try (Connection connection = dataSource.getConnection()) {
            long total;
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM asset" + clause)) {
                bind(statement, params); try (ResultSet rs = statement.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            List<Asset> assets = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM asset" + clause + " ORDER BY asset_code LIMIT ? OFFSET ?")) {
                List<Object> paged = new ArrayList<>(params); paged.add(query.limit()); paged.add(query.offset()); bind(statement, paged);
                try (ResultSet rs = statement.executeQuery()) { while (rs.next()) assets.add(toAsset(rs)); }
            }
            return new AssetPage(assets, total);
        } catch (SQLException e) { throw new AssetStoreException("asset search failed", e); }
    }
    @Override public List<AssetRelation> findOutgoingRelations(AssetId id, Set<RelationType> types) {
        return relations("source_asset_id", id, types);
    }
    @Override public List<AssetRelation> findIncomingRelations(AssetId id, Set<RelationType> types) {
        return relations("target_asset_id", id, types);
    }
    @Override public Optional<DatabaseDetail> findDatabaseDetail(AssetId id) {
        String sql = "SELECT * FROM database_detail WHERE asset_id = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, id.value()); try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new DatabaseDetail(id, DatabaseType.valueOf(rs.getString("database_type")), rs.getString("host"), rs.getInt("port"), rs.getString("service_name"), rs.getString("credential_ref"), rs.getInt("read_only") == 1, rs.getInt("user_unlock_enabled") == 1, read(rs.getString("connection_properties"), STRING_MAP)));
            }
        } catch (SQLException e) { throw new AssetStoreException("database detail lookup failed", e); }
    }
    @Override public Optional<Map<String, Object>> findDetail(AssetId id, AssetType type) {
        String table = detailTable(type);
        if (table == null) return Optional.empty();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM " + table + " WHERE asset_id = ?")) {
            s.setString(1, id.value()); try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(detail(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new AssetStoreException("asset detail lookup failed", e); }
    }
    @Override public Asset create(Asset asset, Map<String, Object> detail) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                writeAsset(c, asset, false, -1); writeDetail(c, asset.id(), asset.type(), detail); c.commit(); return asset;
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new AssetStoreException("asset creation failed", e); }
    }
    @Override public Asset update(Asset asset, long expectedVersion, Map<String, Object> detail) {
        Asset updated = new Asset(asset.id(), asset.type(), asset.code(), asset.displayName(), asset.environment(), asset.status(), asset.sourceName(), asset.externalId(), asset.labels(), asset.metadata(), expectedVersion + 1, asset.createdAt(), Instant.now());
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                writeAsset(c, updated, true, expectedVersion); writeDetail(c, updated.id(), updated.type(), detail); c.commit(); return updated;
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new AssetStoreException("asset update failed", e); }
    }
    @Override public Asset updateStatus(AssetId id, AssetStatus status, long expectedVersion) {
        Asset current = findById(id).orElseThrow(() -> new AssetStoreException("asset not found", new SQLException()));
        Asset changed = new Asset(current.id(), current.type(), current.code(), current.displayName(), current.environment(), status, current.sourceName(), current.externalId(), current.labels(), current.metadata(), current.version() + 1, current.createdAt(), Instant.now());
        try (Connection c = dataSource.getConnection()) { writeAsset(c, changed, true, expectedVersion); return changed; }
        catch (SQLException e) { throw new AssetStoreException("asset status update failed", e); }
    }
    @Override public AssetRelation createRelation(AssetRelation relation) {
        String sql = "INSERT INTO asset_relation (id, source_asset_id, relation_type, target_asset_id, attributes_json, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, relation.id()); s.setString(2, relation.sourceAssetId().value()); s.setString(3, relation.type().name()); s.setString(4, relation.targetAssetId().value()); s.setString(5, json(relation.attributes())); s.setString(6, relation.createdAt().toString()); s.executeUpdate(); return relation;
        } catch (SQLException e) { throw new AssetStoreException("relation creation failed", e); }
    }
    @Override public AssetRelation updateRelationAttributes(String relationId, Map<String, Object> attributes) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("UPDATE asset_relation SET attributes_json = ? WHERE id = ?")) {
            s.setString(1, json(attributes)); s.setString(2, relationId); if (s.executeUpdate() != 1) throw new AssetStoreException("relation not found", new SQLException());
        } catch (SQLException e) { throw new AssetStoreException("relation update failed", e); }
        return relationById(relationId);
    }
    @Override public void deleteRelation(String relationId) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("DELETE FROM asset_relation WHERE id = ?")) {
            s.setString(1, relationId); if (s.executeUpdate() != 1) throw new AssetStoreException("relation not found", new SQLException());
        } catch (SQLException e) { throw new AssetStoreException("relation deletion failed", e); }
    }
    private void writeAsset(Connection c, Asset asset, boolean update, long expectedVersion) throws SQLException {
        String sql = update
                ? "UPDATE asset SET asset_type=?, asset_code=?, display_name=?, environment=?, status=?, source_name=?, external_id=?, labels_json=?, metadata_json=?, version=?, updated_at=? WHERE id=? AND version=?"
                : "INSERT INTO asset (id, asset_type, asset_code, display_name, environment, status, source_name, external_id, labels_json, metadata_json, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = c.prepareStatement(sql)) {
            int i = 1; if (!update) s.setString(i++, asset.id().value()); s.setString(i++, asset.type().name()); s.setString(i++, asset.code()); s.setString(i++, asset.displayName()); s.setString(i++, asset.environment()); s.setString(i++, asset.status().name()); s.setString(i++, asset.sourceName()); s.setString(i++, asset.externalId()); s.setString(i++, json(asset.labels())); s.setString(i++, json(asset.metadata())); s.setLong(i++, asset.version()); if (!update) s.setString(i++, asset.createdAt().toString()); s.setString(i++, asset.updatedAt().toString()); if (update) { s.setString(i++, asset.id().value()); s.setLong(i, expectedVersion); }
            if (s.executeUpdate() != 1) throw new AssetVersionConflictException();
        }
    }
    private void writeDetail(Connection c, AssetId id, AssetType type, Map<String, Object> detail) throws SQLException {
        String table = detailTable(type); if (table == null || detail == null || detail.isEmpty()) return;
        Map<String, Object> values = new LinkedHashMap<>(detail); values.remove("asset_id");
        if (type == AssetType.DATABASE_INSTANCE || type == AssetType.DATABASE_SERVICE) {
            values.putIfAbsent("credential_ref", "inline-password"); values.putIfAbsent("connection_properties", Map.of()); values.putIfAbsent("read_only", 1);
        }
        String[] allowed = detailColumns(type); values.keySet().removeIf(key -> !Arrays.asList(allowed).contains(key));
        if (values.isEmpty()) return;
        List<String> columns = new ArrayList<>(); columns.add("asset_id"); columns.addAll(values.keySet());
        String placeholders = String.join(",", Collections.nCopies(columns.size(), "?"));
        String updates = String.join(",", values.keySet().stream().map(key -> key + "=excluded." + key).toList());
        String sql = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ") ON CONFLICT(asset_id) DO UPDATE SET " + updates;
        try (PreparedStatement s = c.prepareStatement(sql)) { s.setString(1, id.value()); int i = 2; for (Object value : values.values()) s.setObject(i++, serialize(value)); s.executeUpdate(); }
    }
    private static String detailTable(AssetType type) { return switch (type) { case HOST -> "host_detail"; case DATABASE_INSTANCE, DATABASE_SERVICE -> "database_detail"; case OGG_DEPLOYMENT -> "ogg_deployment_detail"; case OGG_PROCESS -> "ogg_process_detail"; case CONFIG_RESOURCE -> "config_resource_detail"; default -> null; }; }
    private static String[] detailColumns(AssetType type) { return switch (type) {
        case HOST -> new String[]{"hostname","management_ip","ssh_port","os_type","os_version","architecture","ssh_credential_ref","bastion_asset_id"};
        case DATABASE_INSTANCE, DATABASE_SERVICE -> new String[]{"database_type","database_version","role","host","port","service_name","database_name","tenant_name","cluster_name","connection_properties","credential_ref","read_only","user_unlock_enabled"};
        case OGG_DEPLOYMENT -> new String[]{"ogg_version","deployment_mode","install_home","deployment_home","service_manager_port","admin_server_port","credential_ref"};
        case OGG_PROCESS -> new String[]{"process_type","process_name","parameter_file","report_file","trail_name","enabled"};
        case CONFIG_RESOURCE -> new String[]{"resource_type","logical_name","absolute_path","charset","readable","writable","sensitive","max_read_bytes","masking_policy"}; default -> new String[0]; }; }
    private Map<String, Object> detail(ResultSet rs) throws SQLException { Map<String, Object> result = new LinkedHashMap<>(); ResultSetMetaData metadata = rs.getMetaData(); for (int i = 1; i <= metadata.getColumnCount(); i++) { String key = metadata.getColumnLabel(i); if (key.equals("asset_id") || key.equals("credential_ref") || key.equals("ssh_credential_ref")) continue; Object value = rs.getObject(i); result.put(key, key.equals("connection_properties") && value != null ? sanitizeConnectionProperties(read(String.valueOf(value), OBJECT_MAP)) : value); } return result; }
    private static Map<String, Object> sanitizeConnectionProperties(Map<String, Object> values) { Map<String, Object> clean = new LinkedHashMap<>(values); clean.remove("password"); return clean; }
    private Object serialize(Object value) { return value instanceof Map<?, ?> || value instanceof Collection<?> ? json(value) : value; }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new AssetStoreException("asset JSON serialization failed", e); } }
    private AssetRelation relationById(String relationId) { try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM asset_relation WHERE id = ?")) { s.setString(1, relationId); try (ResultSet rs = s.executeQuery()) { if (!rs.next()) throw new AssetStoreException("relation not found", new SQLException()); return toRelation(rs); } } catch (SQLException e) { throw new AssetStoreException("relation lookup failed", e); } }
    private Optional<Asset> one(String sql, String parameter) {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setString(1, parameter); try (ResultSet rs = s.executeQuery()) { return rs.next() ? Optional.of(toAsset(rs)) : Optional.empty(); }
        } catch (SQLException e) { throw new AssetStoreException("asset lookup failed", e); }
    }
    private List<AssetRelation> relations(String column, AssetId id, Set<RelationType> types) {
        List<Object> parameters = new ArrayList<>(); parameters.add(id.value());
        String sql = "SELECT * FROM asset_relation WHERE " + column + " = ?";
        if (types != null && !types.isEmpty()) {
            sql += " AND relation_type IN (" + String.join(",", Collections.nCopies(types.size(), "?")) + ")";
            types.stream().map(Enum::name).forEach(parameters::add);
        }
        sql += " ORDER BY relation_type, target_asset_id";
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, parameters); List<AssetRelation> result = new ArrayList<>();
            try (ResultSet rs = s.executeQuery()) { while (rs.next()) result.add(toRelation(rs)); }
            return List.copyOf(result);
        } catch (SQLException e) { throw new AssetStoreException("relation lookup failed", e); }
    }
    private Asset toAsset(ResultSet rs) throws SQLException {
        return new Asset(new AssetId(rs.getString("id")), AssetType.valueOf(rs.getString("asset_type")), rs.getString("asset_code"),
                rs.getString("display_name"), rs.getString("environment"), AssetStatus.valueOf(rs.getString("status")),
                rs.getString("source_name"), rs.getString("external_id"), read(rs.getString("labels_json"), STRING_MAP),
                read(rs.getString("metadata_json"), OBJECT_MAP), rs.getLong("version"), Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")));
    }
    private AssetRelation toRelation(ResultSet rs) throws SQLException {
        return new AssetRelation(rs.getString("id"), new AssetId(rs.getString("source_asset_id")), RelationType.valueOf(rs.getString("relation_type")),
                new AssetId(rs.getString("target_asset_id")), read(rs.getString("attributes_json"), OBJECT_MAP), Instant.parse(rs.getString("created_at")));
    }
    private <T> T read(String json, TypeReference<T> type) {
        try { return objectMapper.readValue(json, type); } catch (Exception e) { throw new AssetStoreException("invalid JSON in asset store", e); }
    }
    private static void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) statement.setObject(index + 1, values.get(index));
    }
    public static class AssetStoreException extends RuntimeException { public AssetStoreException(String message, Exception cause) { super(message, cause); } }
}
