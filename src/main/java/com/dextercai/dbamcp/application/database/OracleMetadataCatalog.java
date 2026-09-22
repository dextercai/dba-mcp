package com.dextercai.dbamcp.application.database;

/** Fixed Oracle dictionary queries for schema-wide constraint and index inventories. */
public final class OracleMetadataCatalog {
    public static final String LIST_CONSTRAINTS_SQL = """
            SELECT c.owner, c.table_name, c.constraint_name, c.constraint_type, c.status, c.validated,
                   c.deferrable, c.deferred, c.delete_rule, c.r_owner, rc.table_name AS referenced_table_name,
                   c.r_constraint_name AS referenced_constraint_name, cc.position, cc.column_name
              FROM dba_constraints c
              LEFT JOIN dba_cons_columns cc
                ON cc.owner = c.owner AND cc.constraint_name = c.constraint_name
              LEFT JOIN dba_constraints rc
                ON rc.owner = c.r_owner AND rc.constraint_name = c.r_constraint_name
             WHERE c.owner = ?
               AND c.constraint_type IN ('P', 'U', 'R', 'C')
             ORDER BY c.table_name, c.constraint_name, cc.position
            """;
    public static final String LIST_INDEXES_SQL = """
            SELECT i.owner AS index_owner, i.table_owner, i.table_name, i.index_name, i.index_type,
                   i.uniqueness, i.status, i.visibility, i.tablespace_name, i.partitioned,
                   ic.column_position, ic.column_name, ic.descend
              FROM dba_indexes i
              JOIN dba_ind_columns ic
                ON ic.index_owner = i.owner AND ic.index_name = i.index_name
             WHERE i.table_owner = ?
             ORDER BY i.table_name, i.index_name, ic.column_position
            """;

    private OracleMetadataCatalog() {
    }
}
