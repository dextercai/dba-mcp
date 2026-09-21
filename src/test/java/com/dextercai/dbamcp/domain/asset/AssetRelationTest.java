package com.dextercai.dbamcp.domain.asset;

import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssetRelationTest {
    @Test void rejectsSelfRelations() {
        AssetId id = new AssetId("asset-1");
        assertThrows(IllegalArgumentException.class, () -> new AssetRelation("relation-1", id, RelationType.RUNS_ON, id, null, Instant.now()));
    }
}
