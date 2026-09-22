package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DatabaseUserCatalogTest {
    @Test
    void fixedUserQueryIsReadOnlyAndExcludesSensitiveColumns() {
        String sql = DatabaseUserCatalog.LIST_USERS_SQL.toUpperCase();

        assertDoesNotThrow(() -> new ReadOnlySqlPolicy().validate(DatabaseUserCatalog.LIST_USERS_SQL));
        assertTrue(sql.contains("ACCOUNT_STATUS"));
        assertTrue(sql.contains(" AS LOCKED"));
        assertFalse(sql.contains("PASSWORD"));
        assertFalse(sql.contains("LAST_LOGIN"));
    }

    @Test
    void unlockPreflightUsesFixedReadOnlyDictionaryQueries() {
        ReadOnlySqlPolicy policy = new ReadOnlySqlPolicy();
        assertDoesNotThrow(() -> policy.validate(OracleUserUnlockCatalog.ACCOUNT_STATUS_SQL));
        assertDoesNotThrow(() -> policy.validate(OracleUserUnlockCatalog.HIGH_PRIVILEGES_SQL));
        String sql = OracleUserUnlockCatalog.HIGH_PRIVILEGES_SQL.toUpperCase();
        assertTrue(sql.contains("DBA_ROLE_PRIVS"));
        assertTrue(sql.contains("DBA_SYS_PRIVS"));
        assertTrue(sql.contains("V$PWFILE_USERS"));
        assertTrue(OracleUserUnlockPolicy.denies("ROLE", "DBA"));
        assertTrue(OracleUserUnlockPolicy.denies("SYSTEM_PRIVILEGE", "ALTER USER"));
        assertTrue(OracleUserUnlockPolicy.denies("SYSTEM_PRIVILEGE", "CREATE ANY TABLE"));
        assertTrue(OracleUserUnlockPolicy.denies("PASSWORD_FILE_PRIVILEGE", "SYS"));
        assertFalse(OracleUserUnlockPolicy.denies("ROLE", "CONNECT"));
    }
}
