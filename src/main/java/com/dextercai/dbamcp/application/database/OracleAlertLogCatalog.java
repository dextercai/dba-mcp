package com.dextercai.dbamcp.application.database;

/** Fixed current-container Oracle ADR alert-log queries. */
public final class OracleAlertLogCatalog {
    public static final String LIST_EVENTS_METADATA_SQL = """
            SELECT originating_timestamp, record_id, message_type, message_level, problem_key
              FROM (
                    SELECT originating_timestamp, record_id, message_type, message_level, problem_key,
                           ROWNUM AS page_row_number
                      FROM (
                            SELECT originating_timestamp, record_id, message_type, message_level, problem_key
                              FROM v$diag_alert_ext
                             ORDER BY originating_timestamp DESC, record_id DESC
                           )
                     WHERE ROWNUM <= ?
                   )
             WHERE page_row_number > ?
             ORDER BY page_row_number
            """;
    public static final String LIST_EVENTS_WITH_MESSAGE_SQL = """
            SELECT originating_timestamp, record_id, message_type, message_level, problem_key, message_text
              FROM (
                    SELECT originating_timestamp, record_id, message_type, message_level, problem_key, message_text,
                           ROWNUM AS page_row_number
                      FROM (
                            SELECT originating_timestamp, record_id, message_type, message_level, problem_key, message_text
                              FROM v$diag_alert_ext
                             ORDER BY originating_timestamp DESC, record_id DESC
                           )
                     WHERE ROWNUM <= ?
                   )
             WHERE page_row_number > ?
             ORDER BY page_row_number
            """;

    private OracleAlertLogCatalog() {
    }
}
