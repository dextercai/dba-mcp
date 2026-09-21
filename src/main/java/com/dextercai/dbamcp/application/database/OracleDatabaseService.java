package com.dextercai.dbamcp.application.database;

import com.dextercai.dbamcp.domain.asset.*;
import com.dextercai.dbamcp.domain.database.*;
import com.dextercai.dbamcp.config.DbaProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class OracleDatabaseService implements AutoCloseable {
    private static final int MAX_ROWS = 500, MAX_CELL_BYTES = 262_144, MAX_RESULT_BYTES = 4_194_304;
    private final AssetRepository assets; private final DbaProperties.Hikari hikari; private final DbaProperties.TargetPools targetPools; private final ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();
    private final BoundedTargetDataSourceRegistry pools;
    public OracleDatabaseService(AssetRepository assets, DbaProperties properties) {
        this.assets = assets; this.hikari = properties.database().hikari(); this.targetPools = properties.database().targetPools();
        this.pools = new BoundedTargetDataSourceRegistry(targetPools, this::createPool);
    }
    public Map<String, String> testConnection(String assetId) {
        try (Connection c = directConnection(new AssetId(assetId))) { DatabaseMetaData meta = c.getMetaData(); return Map.of("product", meta.getDatabaseProductName(), "version", meta.getDatabaseProductVersion(), "user", meta.getUserName()); }
        catch (SQLException e) { throw new DatabaseOperationException("connection failed", e); }
    }
    public QueryResult query(String assetId, String sql, Integer maxRows) {
        return query(assetId, sql, maxRows, statement -> { });
    }
    private QueryResult query(String assetId, String sql, Integer maxRows, StatementBinder binder) {
        policy.validate(sql); int limit = maxRows == null ? MAX_ROWS : Math.min(Math.max(1, maxRows), MAX_ROWS); long started = System.nanoTime();
        try {
            return pools.withDataSource(new AssetId(assetId), dataSource -> executeQuery(dataSource, sql, limit, binder, started));
        } catch (ReadOnlySqlPolicy.QueryRejectedException e) { throw e; } catch (SQLException e) { throw new DatabaseOperationException("query failed", e); }
    }
    private QueryResult executeQuery(HikariDataSource dataSource, String sql, int limit, StatementBinder binder, long started) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setQueryTimeout(30); s.setMaxRows(limit + 1); binder.bind(s); try (ResultSet rs = s.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData(); List<String> columns = new ArrayList<>(); for (int i = 1; i <= meta.getColumnCount(); i++) columns.add(meta.getColumnLabel(i));
                List<Map<String, Object>> rows = new ArrayList<>(); boolean truncated = false; int bytes = 0;
                while (rs.next()) { if (rows.size() == limit) { truncated = true; break; } Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns.size(); i++) { Object value = rs.getObject(i); String rendered = String.valueOf(value); if (rendered.length() > MAX_CELL_BYTES) { rendered = rendered.substring(0, MAX_CELL_BYTES); truncated = true; } bytes += rendered.length(); if (bytes > MAX_RESULT_BYTES) { truncated = true; break; } row.put(columns.get(i - 1), value == null ? null : rendered); }
                    if (bytes > MAX_RESULT_BYTES) break; rows.add(row);
                }
                return new QueryResult(UUID.randomUUID().toString(), columns, rows, rows.size(), truncated, Duration.ofNanos(System.nanoTime() - started).toMillis());
            }
        }
    }
    /** Lists Oracle accounts through a fixed, bounded DBA_USERS query. */
    public QueryResult listUsers(String assetId) {
        return query(assetId, DatabaseUserCatalog.LIST_USERS_SQL, null);
    }
    /** Lists Oracle user-backed schemas through a fixed, bounded DBA_USERS query. */
    public QueryResult listSchemas(String assetId) {
        return query(assetId, SchemaCatalog.LIST_SCHEMAS, null);
    }
    /** Lists tables belonging to one conventional, unquoted Oracle schema identifier. */
    public QueryResult listTables(String assetId, String owner) {
        String normalizedOwner = OracleIdentifier.normalize("owner", owner);
        return query(assetId, SchemaCatalog.LIST_TABLES, null, statement -> statement.setString(1, normalizedOwner));
    }
    /** Lists permanent and temporary tablespace capacity through fixed dictionary views. */
    public QueryResult listTablespaceUsage(String assetId) {
        return query(assetId, TablespaceCatalog.LIST_USAGE_SQL, null);
    }
    /** Returns a bounded aggregate of user sessions without exposing client-identifying fields. */
    public QueryResult getSessionSummary(String assetId) {
        return query(assetId, SessionCatalog.SESSION_SUMMARY_SQL, null);
    }
    /** Returns bounded metadata for open user transactions without SQL text or transaction identifiers. */
    public QueryResult listLongRunningTransactions(String assetId) {
        return query(assetId, SessionCatalog.LONG_RUNNING_TRANSACTIONS_SQL, null);
    }
    /** Returns bounded waiter/blocker metadata from the local Oracle instance without SQL text. */
    public QueryResult listBlockingSessions(String assetId) {
        return query(assetId, SessionCatalog.BLOCKING_SESSIONS_SQL, null);
    }
    /** Returns a fixed, bound-parameter Oracle dictionary description of one conventional table name. */
    public TableDefinition describeTable(String assetId, String owner, String tableName) {
        String normalizedOwner = OracleIdentifier.normalize("owner", owner);
        String normalizedTable = OracleIdentifier.normalize("tableName", tableName);
        try {
            return pools.withDataSource(new AssetId(assetId), connectionPool -> {
                try (Connection connection = connectionPool.getConnection()) {
            TableDefinition.TableAttributes table = readTable(connection, normalizedOwner, normalizedTable);
            return new TableDefinition(table, readColumns(connection, normalizedOwner, normalizedTable),
                    readConstraints(connection, normalizedOwner, normalizedTable),
                    readIndexes(connection, normalizedOwner, normalizedTable));
                }
            });
        } catch (SQLException e) { throw new DatabaseOperationException("table definition query failed", e); }
    }
    private TableDefinition.TableAttributes readTable(Connection connection, String owner, String tableName) throws SQLException {
        try (PreparedStatement statement = dictionaryStatement(connection, TableDefinitionCatalog.TABLE, owner, tableName);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new DatabaseOperationException("table not found");
            return new TableDefinition.TableAttributes(result.getString(1), result.getString(2), result.getString(3),
                    result.getString(4), result.getString(5), result.getString(6), result.getString(7), result.getString(8));
        }
    }
    private List<TableDefinition.ColumnDefinition> readColumns(Connection connection, String owner, String tableName) throws SQLException {
        List<TableDefinition.ColumnDefinition> columns = new ArrayList<>();
        try (PreparedStatement statement = dictionaryStatement(connection, TableDefinitionCatalog.COLUMNS, owner, tableName);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) columns.add(new TableDefinition.ColumnDefinition(integer(result, 1), result.getString(2), result.getString(3), integer(result, 4), integer(result, 5), integer(result, 6), result.getString(7), result.getString(8), result.getString(9), result.getString(10)));
        }
        return columns;
    }
    private List<TableDefinition.ConstraintColumn> readConstraints(Connection connection, String owner, String tableName) throws SQLException {
        List<TableDefinition.ConstraintColumn> constraints = new ArrayList<>();
        try (PreparedStatement statement = dictionaryStatement(connection, TableDefinitionCatalog.CONSTRAINTS, owner, tableName);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) constraints.add(new TableDefinition.ConstraintColumn(result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5), result.getString(6), result.getString(7), result.getString(8), result.getString(9), integer(result, 10), result.getString(11)));
        }
        return constraints;
    }
    private List<TableDefinition.IndexColumn> readIndexes(Connection connection, String owner, String tableName) throws SQLException {
        List<TableDefinition.IndexColumn> indexes = new ArrayList<>();
        try (PreparedStatement statement = dictionaryStatement(connection, TableDefinitionCatalog.INDEXES, owner, tableName);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) indexes.add(new TableDefinition.IndexColumn(result.getString(1), result.getString(2), result.getString(3), result.getString(4), result.getString(5), result.getString(6), integer(result, 7), result.getString(8), result.getString(9)));
        }
        return indexes;
    }
    private static PreparedStatement dictionaryStatement(Connection connection, String sql, String owner, String tableName) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql); statement.setQueryTimeout(30); statement.setString(1, owner); statement.setString(2, tableName); return statement;
    }
    private static Integer integer(ResultSet result, int index) throws SQLException { int value = result.getInt(index); return result.wasNull() ? null : value; }
    @FunctionalInterface private interface StatementBinder { void bind(PreparedStatement statement) throws SQLException; }
    private Connection directConnection(AssetId id) throws SQLException {
        DatabaseDetail detail = databaseDetail(id);
        Properties properties = new Properties();
        detail.connectionProperties().forEach((key, value) -> { if (!key.equals("jdbcUrl")) properties.setProperty(key, value); });
        String url = jdbcUrl(detail);
        return DriverManager.getConnection(url, properties);
    }
    private HikariDataSource createPool(AssetId id) {
        DatabaseDetail detail = databaseDetail(id);
        String url = jdbcUrl(detail);
        HikariConfig config = new HikariConfig(); config.setJdbcUrl(url); config.setMaximumPoolSize(targetPools.maximumPoolSize()); config.setMinimumIdle(0);
        config.setConnectionTimeout(hikari.connectionTimeout().toMillis());
        config.setValidationTimeout(hikari.validationTimeout().toMillis());
        if (!hikari.keepaliveTime().isZero()) config.setKeepaliveTime(hikari.keepaliveTime().toMillis());
        config.setMaxLifetime(hikari.maxLifetime().toMillis());
        config.setIdleTimeout(hikari.idleTimeout().toMillis());
        config.setPoolName("oracle-" + id.value());
        String username = detail.connectionProperties().get("username"); String password = detail.connectionProperties().get("password");
        if (username != null) config.setUsername(username);
        if (password != null) config.setPassword(password);
        detail.connectionProperties().forEach((key, value) -> { if (!key.equals("jdbcUrl") && !key.equals("username") && !key.equals("password")) config.addDataSourceProperty(key, value); });
        return new HikariDataSource(config);
    }
    private DatabaseDetail databaseDetail(AssetId id) {
        DatabaseDetail detail = assets.findDatabaseDetail(id).orElseThrow(() -> new DatabaseOperationException("database detail not found"));
        if (detail.type() != DatabaseType.ORACLE || !detail.readOnly()) throw new DatabaseOperationException("only read-only Oracle assets are enabled");
        return detail;
    }
    private static String jdbcUrl(DatabaseDetail detail) { return detail.connectionProperties().getOrDefault("jdbcUrl", "jdbc:oracle:thin:@//" + detail.host() + ":" + detail.port() + "/" + detail.serviceName()); }
    @Override public void close() { pools.close(); }
    public static class DatabaseOperationException extends RuntimeException { public DatabaseOperationException(String message) { super(message); } public DatabaseOperationException(String message, Exception cause) { super(message, cause); } }
}
