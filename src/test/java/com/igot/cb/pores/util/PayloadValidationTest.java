package com.igot.cb.pores.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.exceptions.CustomException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PayloadValidationTest {

    private PayloadValidation payloadValidation;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        payloadValidation = new PayloadValidation();
        mapper = new ObjectMapper();
    }

    @Test
    void testValidatePayload_withValidSingleObject() throws Exception {
        JsonNode payload = mapper.readTree("{\"key\": \"value\"}");
        JsonSchema schema = mock(JsonSchema.class);
        when(schema.validate(any())).thenReturn(Collections.emptySet());
        JsonSchemaFactory factory = mock(JsonSchemaFactory.class);
        when(factory.getSchema(any(InputStream.class))).thenReturn(schema);

        try (MockedStatic<JsonSchemaFactory> mockedStatic = mockStatic(JsonSchemaFactory.class)) {
            // mock both possible overloads for maximum compatibility
            mockedStatic.when(() -> JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                    .thenReturn(factory);
            mockedStatic.when(JsonSchemaFactory::getInstance)
                    .thenReturn(factory);

            payloadValidation.validatePayload("/payloadValidation/demandValidationData.json", payload);
        }
        verify(factory).getSchema(any(InputStream.class));
        verify(schema).validate(any());
    }


    @Test
    void testValidatePayload_withInvalidObject_throwsException() throws Exception {
        JsonNode payload = mapper.readTree("{\"key\": \"value\"}");
        ValidationMessage message = mock(ValidationMessage.class);
        when(message.getMessage()).thenReturn("field 'name' is missing");

        Set<ValidationMessage> messages = new HashSet<>();
        messages.add(message);

        JsonSchema schema = mock(JsonSchema.class);
        when(schema.validate(any())).thenReturn(messages);

        JsonSchemaFactory factory = mock(JsonSchemaFactory.class);
        when(factory.getSchema(any(InputStream.class))).thenReturn(schema);

        try (MockedStatic<JsonSchemaFactory> mockedStatic = mockStatic(JsonSchemaFactory.class)) {
            mockedStatic.when(() -> JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                    .thenReturn(factory);
            mockedStatic.when(JsonSchemaFactory::getInstance)
                    .thenReturn(factory);

            CustomException ex = assertThrows(CustomException.class,
                    () -> payloadValidation.validatePayload("/payloadValidation/demandValidationData.json", payload));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatusCode());
            assertTrue(
                    ex.getMessage().toLowerCase().contains("validation error"),
                    "Expected message to contain 'validation error' but was: " + ex.getMessage()
            );
        }
    }



    @Test
    void testValidatePayload_withFactoryThrowsException() throws Exception {
        JsonNode payload = mapper.readTree("{\"key\": \"value\"}");

        try (MockedStatic<JsonSchemaFactory> mockedStatic = mockStatic(JsonSchemaFactory.class)) {
            // Mock both overloads for completeness
            mockedStatic.when(() -> JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
                    .thenThrow(new RuntimeException("Factory error"));
            mockedStatic.when(JsonSchemaFactory::getInstance)
                    .thenThrow(new RuntimeException("Factory error"));

            Exception ex = assertThrows(Exception.class,
                    () -> payloadValidation.validatePayload("/schema.json", payload));

            String msg = ex.getMessage().toLowerCase();
            assertTrue(
                    msg.contains("factory error") || msg.contains("failed to validate payload"),
                    "Expected message to contain 'factory error' or 'failed to validate payload' but got: " + msg
            );
        }
    }



}
