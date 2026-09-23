package com.dextercai.dbamcp.application.database;

import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import com.dextercai.dbamcp.domain.database.DatabaseType;

/** Applies the read-only flag only to the dedicated controlled-write exception. */
final class OracleTargetPolicy {
    private OracleTargetPolicy() { }

    static void requireNonMutatingToolTarget(DatabaseDetail detail) {
        requireOracle(detail);
    }

    static void requireUserUnlockTarget(DatabaseDetail detail) {
        requireOracle(detail);
        if (detail.readOnly()) throw new OracleDatabaseService.DatabaseOperationException("Oracle user unlock requires an asset not marked read-only");
    }

    private static void requireOracle(DatabaseDetail detail) {
        if (detail.type() != DatabaseType.ORACLE) throw new OracleDatabaseService.DatabaseOperationException("only Oracle assets are enabled");
    }
}
