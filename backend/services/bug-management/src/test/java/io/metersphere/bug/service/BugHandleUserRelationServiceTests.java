package io.metersphere.bug.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BugHandleUserRelationServiceTests {

    private final BugHandleUserRelationService service = new BugHandleUserRelationService();

    @Test
    void shouldParseSingleCommaAndJsonHandlerValues() {
        assertEquals(List.of("user-a"), service.parse("user-a"));
        assertEquals(List.of("user-a", "user-b"), service.parse(" user-a,user-b,user-a "));
        assertEquals(List.of("user-a", "user-b"), service.parse("[\"user-a\", \"user-b\"]"));
        assertEquals(List.of(), service.parse("  "));
    }

    @Test
    void shouldNormalizeHandlersWithoutDuplicates() {
        assertEquals("user-a,user-b", service.normalize("[\"user-a\",\"user-b\",\"user-a\"]"));
    }
}
