package com.dextercai.dbamcp.application.database;

/** Fixed Oracle data-dictionary queries for schema and table inventory. */
public final class SchemaCatalog {
    public static final String LIST_SCHEMAS = """
            SELECT username AS schema_name, account_status, created, lock_date, expiry_date,
                   profile, authentication_type
              FROM dba_users
             ORDER BY username
            """;
    public static final String LIST_TABLES = """
            SELECT owner, table_name, tablespace_name, temporary, partitioned, iot_type,
                   num_rows, last_analyzed
              FROM dba_tables
             WHERE owner = ?
             ORDER BY table_name
            """;

    private SchemaCatalog() {
    }
}
