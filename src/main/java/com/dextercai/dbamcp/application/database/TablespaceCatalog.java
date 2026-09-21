package com.dextercai.dbamcp.application.database;

/** Fixed, read-only Oracle data-dictionary query that exposes source fields without derived values. */
public final class TablespaceCatalog {
    public static final String LIST_USAGE_SQL = """
            SELECT 'DBA_TABLESPACE_USAGE_METRICS' AS source_view,
                   m.tablespace_name,
                   t.contents AS tablespace_type,
                   CAST(NULL AS NUMBER) AS file_id,
                   CAST(NULL AS VARCHAR2(512)) AS file_name,
                   CAST(NULL AS NUMBER) AS bytes,
                   CAST(NULL AS NUMBER) AS maxbytes,
                   CAST(NULL AS VARCHAR2(3)) AS autoextensible,
                   m.tablespace_size,
                   m.used_space,
                   m.used_percent,
                   t.block_size,
                   CAST(NULL AS NUMBER) AS bytes_used,
                   CAST(NULL AS NUMBER) AS bytes_free,
                   CAST(NULL AS NUMBER) AS con_id
              FROM dba_tablespace_usage_metrics m
              JOIN dba_tablespaces t ON t.tablespace_name = m.tablespace_name
            UNION ALL
            SELECT 'DBA_DATA_FILES' AS source_view,
                   f.tablespace_name,
                   CAST(NULL AS VARCHAR2(30)) AS tablespace_type,
                   f.file_id,
                   f.file_name,
                   f.bytes,
                   f.maxbytes,
                   f.autoextensible,
                   CAST(NULL AS NUMBER) AS tablespace_size,
                   CAST(NULL AS NUMBER) AS used_space,
                   CAST(NULL AS NUMBER) AS used_percent,
                   CAST(NULL AS NUMBER) AS block_size,
                   CAST(NULL AS NUMBER) AS bytes_used,
                   CAST(NULL AS NUMBER) AS bytes_free,
                   CAST(NULL AS NUMBER) AS con_id
              FROM dba_data_files f
            UNION ALL
            SELECT 'DBA_TEMP_FILES' AS source_view,
                   f.tablespace_name,
                   'TEMPORARY' AS tablespace_type,
                   f.file_id,
                   f.file_name,
                   f.bytes,
                   f.maxbytes,
                   f.autoextensible,
                   CAST(NULL AS NUMBER) AS tablespace_size,
                   CAST(NULL AS NUMBER) AS used_space,
                   CAST(NULL AS NUMBER) AS used_percent,
                   CAST(NULL AS NUMBER) AS block_size,
                   CAST(NULL AS NUMBER) AS bytes_used,
                   CAST(NULL AS NUMBER) AS bytes_free,
                   CAST(NULL AS NUMBER) AS con_id
              FROM dba_temp_files f
            UNION ALL
            SELECT 'V$TEMP_SPACE_HEADER' AS source_view,
                   h.tablespace_name,
                   'TEMPORARY' AS tablespace_type,
                   h.file_id,
                   CAST(NULL AS VARCHAR2(512)) AS file_name,
                   CAST(NULL AS NUMBER) AS bytes,
                   CAST(NULL AS NUMBER) AS maxbytes,
                   CAST(NULL AS VARCHAR2(3)) AS autoextensible,
                   CAST(NULL AS NUMBER) AS tablespace_size,
                   CAST(NULL AS NUMBER) AS used_space,
                   CAST(NULL AS NUMBER) AS used_percent,
                   CAST(NULL AS NUMBER) AS block_size,
                   h.bytes_used,
                   h.bytes_free,
                   h.con_id
              FROM v$temp_space_header h
             ORDER BY tablespace_name, source_view, file_id
            """;

    private TablespaceCatalog() {
    }
}
