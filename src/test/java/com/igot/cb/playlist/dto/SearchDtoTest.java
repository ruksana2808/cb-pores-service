package com.igot.cb.playlist.dto;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class SearchDtoTest {
    @Test
    void testNoArgsConstructor() {
        SearchDto dto = new SearchDto();
        assertNotNull(dto);
        assertNull(dto.getQuery());
        assertNull(dto.getRequest());
    }

    @Test
    void testAllArgsConstructor() {
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("key", "value");
        String query = "test query";

        SearchDto dto = new SearchDto(requestMap, query);

        assertEquals(requestMap, dto.getRequest());
        assertEquals(query, dto.getQuery());
    }

    @Test
    void testSettersAndGetters() {
        SearchDto dto = new SearchDto();
        HashMap<String, Object> requestMap = new HashMap<>();
        requestMap.put("param1", "value1");
        String query = "searchQuery";

        dto.setRequest(requestMap);
        dto.setQuery(query);

        assertEquals(requestMap, dto.getRequest());
        assertEquals(query, dto.getQuery());
    }

    @Test
    void testEmptyRequestMap() {
        SearchDto dto = new SearchDto();
        dto.setRequest(new HashMap<>());
        assertNotNull(dto.getRequest());
        assertTrue(dto.getRequest().isEmpty());
    }

    @Test
    void testNullRequestMap() {
        SearchDto dto = new SearchDto();
        dto.setRequest(null);
        assertNull(dto.getRequest());
    }
}
