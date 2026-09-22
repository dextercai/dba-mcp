package com.dextercai.dbamcp.application.database;

import java.util.Locale;
import java.util.Set;

/** Conservative allow policy: uncertainty is handled by the caller as a failed preflight. */
final class OracleUserUnlockPolicy {
    private static final Set<String> HIGH_PRIVILEGE_ROLES = Set.of(
            "DBA", "EXP_FULL_DATABASE", "IMP_FULL_DATABASE", "DATAPUMP_EXP_FULL_DATABASE",
            "DATAPUMP_IMP_FULL_DATABASE", "SELECT_CATALOG_ROLE", "EXECUTE_CATALOG_ROLE", "AQ_ADMINISTRATOR_ROLE");
    private static final Set<String> HIGH_SYSTEM_PRIVILEGES = Set.of(
            "ALTER USER", "BECOME USER", "EXEMPT ACCESS POLICY", "EXEMPT REDACTION POLICY",
            "SYSDBA", "SYSOPER", "SYSBACKUP", "SYSDG", "SYSKM");

    private OracleUserUnlockPolicy() { }

    static boolean denies(String type, String privilege) {
        String normalized = privilege == null ? "" : privilege.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "ROLE" -> HIGH_PRIVILEGE_ROLES.contains(normalized);
            case "SYSTEM_PRIVILEGE" -> normalized.contains(" ANY ") || HIGH_SYSTEM_PRIVILEGES.contains(normalized);
            case "PASSWORD_FILE_PRIVILEGE" -> true;
            default -> true;
        };
    }
}
