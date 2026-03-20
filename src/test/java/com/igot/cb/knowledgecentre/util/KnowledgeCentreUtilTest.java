package com.igot.cb.knowledgecentre.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.knowledgecentre.entity.KnowledgeArticleEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeCategoryEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeSubCategoryEntity;
import com.igot.cb.knowledgecentre.repository.KnowledgeArticlesRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeCategoryRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeSubCategoryRepository;
import com.igot.cb.pores.util.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for KnowledgeCentreUtil
 * Tests cover utility methods for string extraction, timestamp formatting, and duplicate checking
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCentreUtilTest {

    @InjectMocks
    private KnowledgeCentreUtil knowledgeCentreUtil;

    @Mock
    private KnowledgeCategoryRepository knowledgeCategoryRepository;

    @Mock
    private KnowledgeSubCategoryRepository knowledgeSubCategoryRepository;

    @Mock
    private KnowledgeArticlesRepository knowledgeArticlesRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ========================= String Value Extraction Tests =========================

    @Test
    void testGetStringValue_WithValidFieldAndValue_ShouldReturnValue() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Test Title");

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "title");

        // Assert
        assertEquals("Test Title", result);
    }

    @Test
    void testGetStringValue_WithNullJsonNode_ShouldReturnNull() {
        // Arrange & Act
        String result = knowledgeCentreUtil.getStringValue(null, "title");

        // Assert
        assertNull(result);
    }

    @Test
    void testGetStringValue_WithNullField_ShouldReturnNull() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.putNull("title");

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "title");

        // Assert
        assertNull(result);
    }

    @Test
    void testGetStringValue_WithEmptyStringField_ShouldReturnNull() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "");

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "title");

        // Assert
        assertNull(result);
    }

    @Test
    void testGetStringValue_WithWhitespaceStringField_ShouldReturnNull() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "   ");

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "title");

        // Assert
        assertNull(result);
    }

    @Test
    void testGetStringValue_WithValueContainingWhitespace_ShouldTrimAndReturn() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "  Test Title  ");

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "title");

        // Assert
        assertEquals("Test Title", result);
    }

    @Test
    void testGetStringValue_WithNumericValue_ShouldConvertToString() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", 12345);

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "id");

        // Assert
        assertEquals("12345", result);
    }

    @Test
    void testGetStringValue_WithBooleanValue_ShouldConvertToString() {
        // Arrange
        ObjectNode node = objectMapper.createObjectNode();
        node.put("isActive", true);

        // Act
        String result = knowledgeCentreUtil.getStringValue(node, "isActive");

        // Assert
        assertEquals("true", result);
    }

    // ========================= Timestamp Formatting Tests =========================

    @Test
    void testFormatTimestampForES_WithValidTimestamp_ShouldReturnISOFormat() {
        // Arrange
        Timestamp timestamp = new Timestamp(1708588200000L); // 2024-02-22 10:00:00 UTC

        // Act
        String result = knowledgeCentreUtil.formatTimestampForES(timestamp);

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("T")); // ISO format contains T
        assertTrue(result.contains(":")); // ISO format contains colons
    }

    @Test
    void testFormatTimestampForES_WithNullTimestamp_ShouldReturnNull() {
        // Arrange & Act
        String result = knowledgeCentreUtil.formatTimestampForES(null);

        // Assert
        assertNull(result);
    }

    @Test
    void testFormatTimestampForES_WithCurrentTimestamp_ShouldReturnValidISOFormat() {
        // Arrange
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        // Act
        String result = knowledgeCentreUtil.formatTimestampForES(currentTime);

        // Assert
        assertNotNull(result);
        // Verify ISO 8601 format pattern
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    void testFormatTimestampForES_ShouldIncludeTimezoneInfo() {
        // Arrange
        Timestamp timestamp = new Timestamp(1708588200000L);

        // Act
        String result = knowledgeCentreUtil.formatTimestampForES(timestamp);

        // Assert
        assertNotNull(result);
        // Should contain timezone offset like +05:30
        assertTrue(result.contains("+") || result.contains("-"));
    }

    @Test
    void testFormatTimestampForES_MultipleTimestamps_ShouldProduceValidFormats() {
        // Arrange
        Timestamp[] timestamps = {
                new Timestamp(0), // Unix epoch
                new Timestamp(System.currentTimeMillis()),
                new Timestamp(1708588200000L)
        };

        // Act & Assert
        for (Timestamp timestamp : timestamps) {
            String result = knowledgeCentreUtil.formatTimestampForES(timestamp);
            assertNotNull(result);
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*"));
        }
    }

    // ========================= Duplicate Category Title Tests =========================

    @Test
    void testIsDuplicateCategoryTitle_ForCreateWithExistingTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Existing Title";
        KnowledgeCategoryEntity entity = new KnowledgeCategoryEntity();

        when(knowledgeCategoryRepository.findByTitle(title))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateCategoryTitle(title, null);

        // Assert
        assertTrue(result);
        verify(knowledgeCategoryRepository).findByTitle(title);
    }

    @Test
    void testIsDuplicateCategoryTitle_ForCreateWithNewTitle_ShouldReturnFalse() {
        // Arrange
        String title = "New Title";

        when(knowledgeCategoryRepository.findByTitle(title))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateCategoryTitle(title, null);

        // Assert
        assertFalse(result);
        verify(knowledgeCategoryRepository).findByTitle(title);
    }

    @Test
    void testIsDuplicateCategoryTitle_ForUpdateWithDifferentTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Different Title";
        String entityId = "entity-123";
        KnowledgeCategoryEntity entity = new KnowledgeCategoryEntity();

        when(knowledgeCategoryRepository.findByTitleAndIdNot(title, entityId))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateCategoryTitle(title, entityId);

        // Assert
        assertTrue(result);
        verify(knowledgeCategoryRepository).findByTitleAndIdNot(title, entityId);
    }

    @Test
    void testIsDuplicateCategoryTitle_ForUpdateWithSameTitle_ShouldReturnFalse() {
        // Arrange
        String title = "Same Title";
        String entityId = "entity-123";

        when(knowledgeCategoryRepository.findByTitleAndIdNot(title, entityId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateCategoryTitle(title, entityId);

        // Assert
        assertFalse(result);
        verify(knowledgeCategoryRepository).findByTitleAndIdNot(title, entityId);
    }

    // ========================= Duplicate SubCategory Title Tests =========================

    @Test
    void testIsDuplicateSubCategoryTitle_ForCreateWithExistingTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Existing SubCategory";
        String categoryId = "parent-category-123";
        KnowledgeSubCategoryEntity entity = new KnowledgeSubCategoryEntity();

        when(knowledgeSubCategoryRepository.findByTitleAndCategoryId(title, categoryId))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, categoryId, null);

        // Assert
        assertTrue(result);
        verify(knowledgeSubCategoryRepository).findByTitleAndCategoryId(title, categoryId);
    }

    @Test
    void testIsDuplicateSubCategoryTitle_ForCreateWithNewTitle_ShouldReturnFalse() {
        // Arrange
        String title = "New SubCategory";
        String categoryId = "parent-category-123";

        when(knowledgeSubCategoryRepository.findByTitleAndCategoryId(title, categoryId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, categoryId, null);

        // Assert
        assertFalse(result);
        verify(knowledgeSubCategoryRepository).findByTitleAndCategoryId(title, categoryId);
    }

    @Test
    void testIsDuplicateSubCategoryTitle_ForUpdateWithDifferentTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Different SubCategory";
        String categoryId = "parent-category-123";
        String entityId = "entity-456";
        KnowledgeSubCategoryEntity entity = new KnowledgeSubCategoryEntity();

        when(knowledgeSubCategoryRepository.findByTitleAndCategoryIdAndIdNot(title, categoryId, entityId))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, categoryId, entityId);

        // Assert
        assertTrue(result);
        verify(knowledgeSubCategoryRepository).findByTitleAndCategoryIdAndIdNot(title, categoryId, entityId);
    }

    @Test
    void testIsDuplicateSubCategoryTitle_ForUpdateWithSameTitle_ShouldReturnFalse() {
        // Arrange
        String title = "Same SubCategory";
        String categoryId = "parent-category-123";
        String entityId = "entity-456";

        when(knowledgeSubCategoryRepository.findByTitleAndCategoryIdAndIdNot(title, categoryId, entityId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, categoryId, entityId);

        // Assert
        assertFalse(result);
        verify(knowledgeSubCategoryRepository).findByTitleAndCategoryIdAndIdNot(title, categoryId, entityId);
    }

    // ========================= Duplicate Article Title Tests =========================

    @Test
    void testIsDuplicateArticleTitle_ForCreateWithExistingTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Existing Article";
        String subCategoryId = "parent-subcategory-123";
        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();

        when(knowledgeArticlesRepository.findByTitleAndSubCategoryId(title, subCategoryId))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateArticleTitle(title, subCategoryId, null);

        // Assert
        assertTrue(result);
        verify(knowledgeArticlesRepository).findByTitleAndSubCategoryId(title, subCategoryId);
    }

    @Test
    void testIsDuplicateArticleTitle_ForCreateWithNewTitle_ShouldReturnFalse() {
        // Arrange
        String title = "New Article";
        String subCategoryId = "parent-subcategory-123";

        when(knowledgeArticlesRepository.findByTitleAndSubCategoryId(title, subCategoryId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateArticleTitle(title, subCategoryId, null);

        // Assert
        assertFalse(result);
        verify(knowledgeArticlesRepository).findByTitleAndSubCategoryId(title, subCategoryId);
    }

    @Test
    void testIsDuplicateArticleTitle_ForUpdateWithDifferentTitle_ShouldReturnTrue() {
        // Arrange
        String title = "Different Article";
        String subCategoryId = "parent-subcategory-123";
        String entityId = "entity-789";
        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();

        when(knowledgeArticlesRepository.findByTitleAndSubCategoryIdAndIdNot(title, subCategoryId, entityId))
                .thenReturn(Optional.of(entity));

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateArticleTitle(title, subCategoryId, entityId);

        // Assert
        assertTrue(result);
        verify(knowledgeArticlesRepository).findByTitleAndSubCategoryIdAndIdNot(title, subCategoryId, entityId);
    }

    @Test
    void testIsDuplicateArticleTitle_ForUpdateWithSameTitle_ShouldReturnFalse() {
        // Arrange
        String title = "Same Article";
        String subCategoryId = "parent-subcategory-123";
        String entityId = "entity-789";

        when(knowledgeArticlesRepository.findByTitleAndSubCategoryIdAndIdNot(title, subCategoryId, entityId))
                .thenReturn(Optional.empty());

        // Act
        boolean result = knowledgeCentreUtil.isDuplicateArticleTitle(title, subCategoryId, entityId);

        // Assert
        assertFalse(result);
        verify(knowledgeArticlesRepository).findByTitleAndSubCategoryIdAndIdNot(title, subCategoryId, entityId);
    }

    // ========================= Invalid Type Handling Tests =========================

    @Test
    void testHandleInvalidType_WithInvalidTypeName_ShouldSetErrorMessage() {
        // Arrange
        String invalidType = "INVALID_TYPE";
        ApiResponse response = new ApiResponse();

        // Act
        ApiResponse result = knowledgeCentreUtil.handleInvalidType(invalidType, response);

        // Assert
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
        assertNotNull(result.getParams().getErrMsg());
        assertTrue(result.getParams().getErrMsg().contains(invalidType));
    }

    @Test
    void testHandleInvalidType_ShouldReturnBadRequestStatus() {
        // Arrange
        String invalidType = "UNKNOWN";
        ApiResponse response = new ApiResponse();

        // Act
        ApiResponse result = knowledgeCentreUtil.handleInvalidType(invalidType, response);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
    }

    @Test
    void testHandleInvalidType_WithEmptyType_ShouldHandleGracefully() {
        // Arrange
        String emptyType = "";
        ApiResponse response = new ApiResponse();

        // Act
        ApiResponse result = knowledgeCentreUtil.handleInvalidType(emptyType, response);

        // Assert
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
    }

    @Test
    void testHandleInvalidType_WithSpecialCharacters_ShouldIncludeInErrorMessage() {
        // Arrange
        String specialType = "INVALID@#$%";
        ApiResponse response = new ApiResponse();

        // Act
        ApiResponse result = knowledgeCentreUtil.handleInvalidType(specialType, response);

        // Assert
        assertNotNull(result);
        assertTrue(result.getParams().getErrMsg().contains(specialType));
    }
}

