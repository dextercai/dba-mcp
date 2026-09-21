package com.dextercai.dbamcp.application.database;

/** Fixed Oracle dictionary queries used by {@link OracleDatabaseService#describeTable(String, String, String)}. */
public final class TableDefinitionCatalog {
    public static final String TABLE = """
            SELECT owner, table_name, tablespace_name, temporary, partitioned, iot_type, compression, logging
              FROM dba_tables
             WHERE owner = ? AND table_name = ?
            """;
    public static final String COLUMNS = """
            SELECT c.column_id, c.column_name, c.data_type, c.data_length, c.data_precision, c.data_scale,
                   c.nullable, c.identity_column, c.virtual_column, cc.comments
              FROM dba_tab_cols c
              LEFT JOIN dba_col_comments cc
                ON cc.owner = c.owner AND cc.table_name = c.table_name AND cc.column_name = c.column_name
             WHERE c.owner = ? AND c.table_name = ?
             ORDER BY c.column_id
            """;
    public static final String CONSTRAINTS = """
            SELECT c.constraint_name, c.constraint_type, c.status, c.validated, c.deferrable, c.deferred,
                   c.delete_rule, c.r_owner, c.r_constraint_name, cc.position, cc.column_name
              FROM dba_constraints c
              LEFT JOIN dba_cons_columns cc
                ON cc.owner = c.owner AND cc.constraint_name = c.constraint_name
             WHERE c.owner = ? AND c.table_name = ?
               AND c.constraint_type IN ('P', 'U', 'R', 'C')
             ORDER BY c.constraint_name, cc.position
            """;
    public static final String INDEXES = """
            SELECT i.index_name, i.index_type, i.uniqueness, i.status, i.visibility, i.tablespace_name,
                   ic.column_position, ic.column_name, ic.descend
              FROM dba_indexes i
              JOIN dba_ind_columns ic
                ON ic.index_owner = i.owner AND ic.index_name = i.index_name
             WHERE i.table_owner = ? AND i.table_name = ?
             ORDER BY i.index_name, ic.column_position
            """;

    private TableDefinitionCatalog() {
    }
}
