package com.dextercai.dbamcp.application.database;

/** Server-owned statements for the narrowly scoped Oracle account-unlock operation. */
public final class OracleUserUnlockCatalog {
    public static final String ACCOUNT_STATUS_SQL = "SELECT account_status FROM dba_users WHERE username = ?";
    public static final String HIGH_PRIVILEGES_SQL = """
            SELECT 'ROLE' AS privilege_type, granted_role AS privilege_name
              FROM dba_role_privs
             START WITH grantee = ?
           CONNECT BY NOCYCLE PRIOR granted_role = grantee
            UNION ALL
            SELECT 'SYSTEM_PRIVILEGE', privilege
              FROM dba_sys_privs
             WHERE grantee = ?
            UNION ALL
            SELECT 'PASSWORD_FILE_PRIVILEGE', username
              FROM v$pwfile_users
             WHERE username = ?
               AND (sysdba = 'TRUE' OR sysoper = 'TRUE' OR sysbackup = 'TRUE'
                    OR sysdg = 'TRUE' OR syskm = 'TRUE')
            """;

    private OracleUserUnlockCatalog() { }
}
