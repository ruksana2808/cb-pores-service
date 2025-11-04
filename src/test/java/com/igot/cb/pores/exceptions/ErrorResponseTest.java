package com.igot.cb.pores.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorResponseTest {

    @Test
    void testBuilderAndGetters() {
        ErrorResponse error = ErrorResponse.builder()
                .code("ERR001")
                .message("Invalid request")
                .httpStatusCode(HttpStatus.BAD_REQUEST.value())
                .build();

        assertEquals("ERR001", error.getCode());
        assertEquals("Invalid request", error.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST.value(), error.getHttpStatusCode());
    }

    @Test
    void testEqualsAndHashCode() {
        ErrorResponse e1 = ErrorResponse.builder()
                .code("ERR001")
                .message("Invalid request")
                .httpStatusCode(400)
                .build();

        ErrorResponse e2 = ErrorResponse.builder()
                .code("ERR001")
                .message("Invalid request")
                .httpStatusCode(400)
                .build();

        ErrorResponse e3 = ErrorResponse.builder()
                .code("ERR002")
                .message("Other")
                .httpStatusCode(404)
                .build();

        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
        assertNotEquals(e1, e3);
    }

    @Test
    void testToStringContainsFields() {
        ErrorResponse error = ErrorResponse.builder()
                .code("ERR003")
                .message("Something went wrong")
                .httpStatusCode(500)
                .build();

        String s = error.toString();
        assertTrue(s.contains("ERR003"));
        assertTrue(s.contains("Something went wrong"));
        assertTrue(s.contains("500"));
    }

    @Test
    void testImmutability() {
        ErrorResponse error = ErrorResponse.builder()
                .code("ERR004")
                .message("Immutable test")
                .httpStatusCode(200)
                .build();

        assertThrows(NoSuchMethodException.class,
                () -> error.getClass().getDeclaredMethod("setCode", String.class));
    }
}
