package com.dextercai.dbamcp.application.database;

/**
 * Fixed Oracle data-dictionary query for non-sensitive account administration
 * metadata.  Keeping this statement server-defined avoids turning the user
 * inventory tool into another arbitrary-SQL entry point.
 */
public final class DatabaseUserCatalog {
    public static final String LIST_USERS_SQL = """
            SELECT username,
                   account_status,
                   CASE WHEN account_status LIKE '%LOCKED%' THEN 'true' ELSE 'false' END AS locked,
                   created,
                   lock_date,
                   expiry_date,
                   profile,
                   authentication_type
              FROM dba_users
             ORDER BY username
            """;

    private DatabaseUserCatalog() {
    }
}
