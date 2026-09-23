package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OraclePageRequestTest {
    @Test
    void defaultsToTheFirstSmallPage() {
        OraclePageRequest page = OraclePageRequest.from(null, null);

        assertEquals(1, page.pageNum());
        assertEquals(100, page.pageSize());
        assertEquals(0, page.offset());
        assertEquals(101, page.fetchSizeWithProbe());
    }

    @Test
    void calculatesTheOffsetForLaterPages() {
        OraclePageRequest page = OraclePageRequest.from(3, 25);

        assertEquals(50, page.offset());
        assertEquals(26, page.fetchSizeWithProbe());
    }

    @Test
    void rejectsOutOfRangePageValues() {
        assertThrows(IllegalArgumentException.class, () -> OraclePageRequest.from(0, 10));
        assertThrows(IllegalArgumentException.class, () -> OraclePageRequest.from(10_001, 10));
        assertThrows(IllegalArgumentException.class, () -> OraclePageRequest.from(1, 0));
        assertThrows(IllegalArgumentException.class, () -> OraclePageRequest.from(1, 501));
    }
}
