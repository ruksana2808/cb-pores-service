package com.igot.cb.knowledgecentre.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.knowledgecentre.service.KnowledgeService;
import com.igot.cb.knowledgecentre.util.KnowledgeCentreUtil;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test cases for KnowledgeController
 * Tests cover all CRUD operations for category, subcategory, and article
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @InjectMocks
    private KnowledgeController knowledgeController;

    @Mock
    private KnowledgeService knowledgeService;

    @Mock
    private KnowledgeCentreUtil knowledgeCentreUtil;

    private ObjectMapper objectMapper;
    private String authToken;
    private String testId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        authToken = "valid-auth-token";
        testId = "test-id-123";
    }

    // ========================= Category Tests =========================

    @Test
    void testCreateCategory_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        mockResponse.setResult(Map.of());

        when(knowledgeService.createCategory(any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.createType(
                Constants.CATEGORY, categoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(knowledgeService).createCategory(any(JsonNode.class), eq(authToken));
    }

    @Test
    void testCreateCategory_WithEmptyJsonNode_ShouldHandleGracefully() {
        // Arrange
        JsonNode emptyNode = objectMapper.createObjectNode();
        ApiResponse errorResponse = new ApiResponse();
        errorResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(knowledgeService.createCategory(any(JsonNode.class), eq(authToken)))
                .thenReturn(errorResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.createType(
                Constants.CATEGORY, emptyNode, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testUpdateCategory_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.updateCategory(eq(testId), any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.updateType(
                Constants.CATEGORY, testId, categoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).updateCategory(eq(testId), any(JsonNode.class), eq(authToken));
    }

    @Test
    void testPublishCategory_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.publishCategory(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                Constants.CATEGORY, Map.of(Constants.ID, testId), authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).publishCategory(testId, authToken);
    }

    @Test
    void testPublishCategory_WithMissingId_ShouldReturnBadRequest() {
        // Arrange - Empty map without ID
        Map<String, String> emptyMap = Map.of();

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                Constants.CATEGORY, emptyMap, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Constants.FAILED, response.getBody().getParams().getStatus());
        verify(knowledgeService, never()).publishCategory(any(), any());
    }

    @Test
    void testPublishCategory_WithEmptyId_ShouldReturnBadRequest() {
        // Arrange - Map with empty string ID
        Map<String, String> mapWithEmptyId = Map.of(Constants.ID, "   ");

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                Constants.CATEGORY, mapWithEmptyId, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(knowledgeService, never()).publishCategory(any(), any());
    }

    @Test
    void testPublishType_WithInvalidType_ShouldReturnBadRequest() {
        // Arrange
        String invalidType = "invalid_type";

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                invalidType, Map.of(Constants.ID, testId), authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getParams().getErrMsg().contains("Invalid type"));
        verify(knowledgeService, never()).publishCategory(any(), any());
        verify(knowledgeService, never()).publishSubCategory(any(), any());
        verify(knowledgeService, never()).publishArticle(any(), any());
    }

    @Test
    void testDeleteCategory_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.deleteCategory(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.deleteType(
                Constants.CATEGORY, testId, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).deleteCategory(testId, authToken);
    }

    // ========================= SubCategory Tests =========================

    @Test
    void testCreateSubCategory_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode subCategoryDto = createValidSubCategoryNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.createSubCategory(any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.createType(
                Constants.SUBCATEGORY, subCategoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).createSubCategory(any(JsonNode.class), eq(authToken));
    }

    @Test
    void testUpdateSubCategory_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode subCategoryDto = createValidSubCategoryNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.updateSubCategory(eq(testId), any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.updateType(
                Constants.SUBCATEGORY, testId, subCategoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).updateSubCategory(eq(testId), any(JsonNode.class), eq(authToken));
    }

    @Test
    void testPublishSubCategory_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.publishSubCategory(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                Constants.SUBCATEGORY, Map.of(Constants.ID, testId), authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).publishSubCategory(testId, authToken);
    }

    @Test
    void testDeleteSubCategory_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.deleteSubCategory(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.deleteType(
                Constants.SUBCATEGORY, testId, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).deleteSubCategory(testId, authToken);
    }

    // ========================= Article Tests =========================

    @Test
    void testCreateArticle_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode articleDto = createValidArticleNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.createArticle(any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.createType(
                Constants.ARTICLE, articleDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).createArticle(any(JsonNode.class), eq(authToken));
    }

    @Test
    void testUpdateArticle_WithValidData_ShouldReturnOk() {
        // Arrange
        JsonNode articleDto = createValidArticleNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.updateArticle(eq(testId), any(JsonNode.class), eq(authToken)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.updateType(
                Constants.ARTICLE, testId, articleDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).updateArticle(eq(testId), any(JsonNode.class), eq(authToken));
    }

    @Test
    void testPublishArticle_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.publishArticle(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.publishType(
                Constants.ARTICLE, Map.of(Constants.ID, testId), authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).publishArticle(testId, authToken);
    }

    @Test
    void testDeleteArticle_WithValidId_ShouldReturnOk() {
        // Arrange
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.deleteArticle(testId, authToken))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.deleteType(
                Constants.ARTICLE, testId, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).deleteArticle(testId, authToken);
    }

    // ========================= Search Tests =========================

    @Test
    void testSearchEntity_WithValidCriteria_ShouldReturnOk() {
        // Arrange
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setSearchString("test");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(knowledgeService.searchEntity(any(SearchCriteria.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.searchEntity(searchCriteria);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(knowledgeService).searchEntity(any(SearchCriteria.class));
    }

    @Test
    void testSearchEntity_WithNullCriteria_ShouldHandleGracefully() {
        // Arrange
        SearchCriteria nullCriteria = null;
        ApiResponse errorResponse = new ApiResponse();
        errorResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(knowledgeService.searchEntity(nullCriteria))
                .thenReturn(errorResponse);

        // Act
        ResponseEntity<ApiResponse> response = knowledgeController.searchEntity(nullCriteria);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ========================= Helper Methods =========================

    private JsonNode createValidCategoryNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Test Category");
        node.put("summary", "Test summary");
        node.put("type", Constants.CATEGORY);
        node.put("isPublic", true);
        return node;
    }

    private JsonNode createValidSubCategoryNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Test SubCategory");
        node.put("categoryId", "parent-category-id");
        node.put("type", Constants.SUBCATEGORY);
        return node;
    }

    private JsonNode createValidArticleNode() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("title", "Test Article");
        node.put("subCategoryId", "parent-subcategory-id");
        node.put("type", Constants.ARTICLE);
        node.put("content", "Article content");
        return node;
    }
}

