package com.dextercai.dbamcp.application.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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

    @Test
    void exposesAnUnambiguousContinuationForNumberedPages() {
        QueryResult result = new QueryResult("execution", List.of("value"), List.of(Map.of("value", "a")),
                1, false, true, null, null, 1).forPage(OraclePageRequest.from(2, 25));

        assertFalse(result.truncated());
        assertTrue(result.hasNextPage());
        assertEquals(3, result.nextPageNum());
        assertEquals(null, result.nextOffset());
    }

    @Test
    void exposesAnUnambiguousContinuationForOffsetPages() {
        QueryResult result = new QueryResult("execution", List.of("value"), List.of(Map.of("value", "a")),
                1, false, true, null, null, 1).forOffset(100, 50);

        assertFalse(result.truncated());
        assertTrue(result.hasNextPage());
        assertEquals(150, result.nextOffset());
        assertEquals(null, result.nextPageNum());
    }
}
