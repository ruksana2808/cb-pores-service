package com.igot.cb.transactional.exceptions;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CassandraPropertyReaderExceptionTest {
    @Test
    void testConstructorAndMessage() {
        Throwable cause = new RuntimeException("Root cause");
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException("Test message", cause);

        assertEquals("Test message", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testConstructorWithNullCause() {
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException("Only message", null);

        assertEquals("Only message", exception.getMessage());
        assertNull(exception.getCause());
    }
}
