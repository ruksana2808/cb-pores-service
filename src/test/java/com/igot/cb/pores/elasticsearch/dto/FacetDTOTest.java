package com.igot.cb.pores.elasticsearch.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FacetDTOTest {
    @Test
    void testAllArgsConstructorAndGetters() {
        FacetDTO dto = new FacetDTO("education", 15L);
        assertEquals("education", dto.getValue());
        assertEquals(15L, dto.getCount());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        FacetDTO dto = new FacetDTO();
        dto.setValue("training");
        dto.setCount(25L);

        assertEquals("training", dto.getValue());
        assertEquals(25L, dto.getCount());
    }

    @Test
    void testSerializableImplementation() {
        assertTrue(dtoIsSerializable(new FacetDTO("test", 1L)));
    }

    private boolean dtoIsSerializable(Object obj) {
        return obj instanceof java.io.Serializable;
    }
}
