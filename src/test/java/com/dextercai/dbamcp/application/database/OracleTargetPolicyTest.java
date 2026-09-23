package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dextercai.dbamcp.domain.asset.AssetId;
import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import com.dextercai.dbamcp.domain.database.DatabaseType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OracleTargetPolicyTest {
    @Test
    void nonMutatingToolsAllowBothReadOnlyAndNonReadOnlyAssets() {
        assertDoesNotThrow(() -> OracleTargetPolicy.requireNonMutatingToolTarget(detail(true)));
        assertDoesNotThrow(() -> OracleTargetPolicy.requireNonMutatingToolTarget(detail(false)));
    }

    @Test
    void userUnlockRequiresDedicatedNonReadOnlyAsset() {
        assertDoesNotThrow(() -> OracleTargetPolicy.requireUserUnlockTarget(detail(false)));
        assertThrows(OracleDatabaseService.DatabaseOperationException.class, () -> OracleTargetPolicy.requireUserUnlockTarget(detail(true)));
    }

    private static DatabaseDetail detail(boolean readOnly) {
        return new DatabaseDetail(new AssetId("oracle-1"), DatabaseType.ORACLE, "oracle.internal", 1521, "ORCL", "inline-password", readOnly, true, Map.of());
    }
}
