package com.igot.cb.knowledgecentre.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.knowledgecentre.entity.KnowledgeArticleEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeCategoryEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeSubCategoryEntity;
import com.igot.cb.knowledgecentre.repository.KnowledgeArticlesRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeCategoryRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeSubCategoryRepository;
import com.igot.cb.knowledgecentre.util.KnowledgeCentreUtil;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import com.igot.cb.knowledgecentre.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for KnowledgeServiceImpl
 * Tests cover create, update, publish, delete, and search operations for all entity types
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeServiceImplTest {

    @InjectMocks
    private KnowledgeServiceImpl knowledgeService;

    @Mock
    private KnowledgeCategoryRepository knowledgeCategoryRepository;

    @Mock
    private KnowledgeSubCategoryRepository knowledgeSubCategoryRepository;

    @Mock
    private KnowledgeArticlesRepository knowledgeArticlesRepository;

    @Mock
    private PayloadValidation payloadValidation;

    @Mock
    private EsUtilService esUtilService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Mock
    private KnowledgeCentreUtil knowledgeCentreUtil;

    @Mock
    private ValueOperations<String, SearchResult> valueOperations;

    @Mock
    private UserService userService;

    private ObjectMapper realObjectMapper;
    private String authToken;
    private String userId;
    private String entityId;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper();
        authToken = "valid-auth-token";
        userId = "user-123";
        entityId = "entity-123";

        // Setup ObjectMapper to return ApiResponse with OK status
        ApiResponse defaultResponse = new ApiResponse();
        defaultResponse.setResponseCode(HttpStatus.OK);
        // Global stubs used by multiple tests (prevents JWT secret null errors)
        lenient().when(cbServerProperties.getJwtSecretKey()).thenReturn("test-secret-key");
        lenient().when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);
    }

    // ========================= Category Tests =========================

    @Test
    void testCreateCategory_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateCategoryTitle(anyString(), isNull())).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeCategoryRepository.save(any(KnowledgeCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.createCategory(categoryDto, authToken);

        // Assert
        assertNotNull(response, "Response should not be null");
    }

    @Test
    void testCreateCategory_WithUnauthorizedToken_ShouldReturnUnauthorized() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(Constants.UNAUTHORIZED);

        // Act
        ApiResponse response = knowledgeService.createCategory(categoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        verify(knowledgeCategoryRepository, never()).save(any());
    }

    @Test
    void testCreateCategory_WithDuplicateTitle_ShouldReturnBadRequest() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateCategoryTitle("Test Category", null)).thenReturn(true);

        // Act
        ApiResponse response = knowledgeService.createCategory(categoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(knowledgeCategoryRepository, never()).save(any());
    }

    @Test
    void testUpdateCategory_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        KnowledgeCategoryEntity existingEntity = createMockCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCategoryRepository.findById(entityId)).thenReturn(Optional.of(existingEntity));
        when(knowledgeCentreUtil.isDuplicateCategoryTitle(anyString(), eq(entityId))).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeCategoryRepository.save(any(KnowledgeCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.updateCategory(entityId, categoryDto, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeCategoryRepository).save(any(KnowledgeCategoryEntity.class));
    }

    @Test
    void testUpdateCategory_WithNonExistentId_ShouldReturnNotFound() {
        // Arrange
        JsonNode categoryDto = createValidCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCategoryRepository.findById(entityId)).thenReturn(Optional.empty());

        // Act
        ApiResponse response = knowledgeService.updateCategory(entityId, categoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        verify(knowledgeCategoryRepository, never()).save(any());
    }

    @Test
    void testPublishCategory_WithValidData_ShouldSucceed() {
        // Arrange
        KnowledgeCategoryEntity entity = createMockCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCategoryRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeCategoryRepository.save(any(KnowledgeCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.publishCategory(entityId, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeCategoryRepository).save(any(KnowledgeCategoryEntity.class));
    }

    @Test
    void testDeleteCategory_ShouldArchiveInsteadOfDelete() {
        // Arrange
        KnowledgeCategoryEntity entity = createMockCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCategoryRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        when(knowledgeCategoryRepository.save(any(KnowledgeCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.deleteCategory(entityId, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeCategoryRepository).save(any(KnowledgeCategoryEntity.class));
    }

    // ========================= SubCategory Tests =========================

    @Test
    void testCreateSubCategory_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode subCategoryDto = createValidSubCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateSubCategoryTitle(anyString(), anyString(), isNull())).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeSubCategoryRepository.save(any(KnowledgeSubCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.createSubCategory(subCategoryDto, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeSubCategoryRepository).save(any(KnowledgeSubCategoryEntity.class));
    }

    @Test
    void testCreateSubCategory_WithDuplicateTitle_ShouldReturnBadRequest() {
        // Arrange
        JsonNode subCategoryDto = createValidSubCategoryNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateSubCategoryTitle(anyString(), anyString(), isNull())).thenReturn(true);

        // Act
        ApiResponse response = knowledgeService.createSubCategory(subCategoryDto, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(knowledgeSubCategoryRepository, never()).save(any());
    }

    @Test
    void testUpdateSubCategory_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode subCategoryDto = createValidSubCategoryNode();
        KnowledgeSubCategoryEntity existingEntity = createMockSubCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeSubCategoryRepository.findById(entityId)).thenReturn(Optional.of(existingEntity));
        when(knowledgeCentreUtil.isDuplicateSubCategoryTitle(anyString(), anyString(), eq(entityId))).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeSubCategoryRepository.save(any(KnowledgeSubCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.updateSubCategory(entityId, subCategoryDto, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeSubCategoryRepository).save(any(KnowledgeSubCategoryEntity.class));
    }

    @Test
    void testPublishSubCategory_WithValidData_ShouldSucceed() {
        // Arrange
        KnowledgeSubCategoryEntity entity = createMockSubCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeSubCategoryRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeSubCategoryRepository.save(any(KnowledgeSubCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.publishSubCategory(entityId, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeSubCategoryRepository).save(any(KnowledgeSubCategoryEntity.class));
    }

    @Test
    void testDeleteSubCategory_ShouldArchiveInsteadOfDelete() {
        // Arrange
        KnowledgeSubCategoryEntity entity = createMockSubCategoryEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeSubCategoryRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        when(knowledgeSubCategoryRepository.save(any(KnowledgeSubCategoryEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.deleteSubCategory(entityId, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeSubCategoryRepository).save(any(KnowledgeSubCategoryEntity.class));
    }

    // ========================= Article Tests =========================

    @Test
    void testCreateArticle_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode articleDto = createValidArticleNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateArticleTitle(anyString(), anyString(), isNull())).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeArticlesRepository.save(any(KnowledgeArticleEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.createArticle(articleDto, authToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(knowledgeArticlesRepository).save(any(KnowledgeArticleEntity.class));
    }

    @Test
    void testCreateArticle_WithDuplicateTitle_ShouldReturnBadRequest() {
        // Arrange
        JsonNode articleDto = createValidArticleNode();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeCentreUtil.isDuplicateArticleTitle(anyString(), anyString(), isNull())).thenReturn(true);

        // Act
        ApiResponse response = knowledgeService.createArticle(articleDto, authToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(knowledgeArticlesRepository, never()).save(any());
    }

    @Test
    void testUpdateArticle_WithValidData_ShouldSucceed() {
        // Arrange
        JsonNode articleDto = createValidArticleNode();
        KnowledgeArticleEntity existingEntity = createMockArticleEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeArticlesRepository.findById(entityId)).thenReturn(Optional.of(existingEntity));
        when(knowledgeCentreUtil.isDuplicateArticleTitle(anyString(), anyString(), eq(entityId))).thenReturn(false);
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeArticlesRepository.save(any(KnowledgeArticleEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.updateArticle(entityId, articleDto, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeArticlesRepository).save(any(KnowledgeArticleEntity.class));
    }

    @Test
    void testPublishArticle_WithValidData_ShouldSucceed() {
        // Arrange
        KnowledgeArticleEntity entity = createMockArticleEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeArticlesRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        doNothing().when(payloadValidation).validatePayload(anyString(), any(JsonNode.class));
        when(knowledgeArticlesRepository.save(any(KnowledgeArticleEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ApiResponse response = knowledgeService.publishArticle(entityId, authToken);

        // Assert
        assertNotNull(response);
        verify(knowledgeArticlesRepository).save(any(KnowledgeArticleEntity.class));
    }

    @Test
    void testDeleteArticle_ShouldArchiveInsteadOfDelete() {
        KnowledgeArticleEntity entity = createMockArticleEntity();
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);
        when(knowledgeArticlesRepository.findById(entityId)).thenReturn(Optional.of(entity));
        when(knowledgeCentreUtil.formatTimestampForES(any(Timestamp.class))).thenReturn("2026-02-23T10:00:00+05:30");
        when(knowledgeArticlesRepository.save(any(KnowledgeArticleEntity.class))).thenAnswer(i -> i.getArgument(0));
        ApiResponse response = knowledgeService.deleteArticle(entityId, authToken);
        assertNotNull(response);
        verify(knowledgeArticlesRepository).save(any(KnowledgeArticleEntity.class));
    }


    @Test
    void testSearchEntity_WithValidCriteria_ShouldReturnResults() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("test");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(cbServerProperties.getJwtSecretKey()).thenReturn("test-secret-key");
        when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);

        ApiResponse response = knowledgeService.searchEntity(criteria);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testSearchEntity_WithMinimumCharacters_ShouldReturnError() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("a");

        ApiResponse response = knowledgeService.searchEntity(criteria);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(esUtilService, never()).searchDocumentsV2(anyString(), any());
    }

    @Test
    void testSearchEntity_WithCachedResult_ShouldReturnFromRedis() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("test");
        SearchResult cachedResult = new SearchResult();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedResult);
        when(cbServerProperties.getJwtSecretKey()).thenReturn("test-secret-key");
        ApiResponse response = knowledgeService.searchEntity(criteria);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(esUtilService, never()).searchDocumentsV2(anyString(), any());
    }

    // ========================= SPV Search Tests =========================

    /**
     * Parameterized test for successful search scenarios
     */
    @ParameterizedTest(name = "Search with searchString=''{0}'' should return OK")
    @MethodSource("provideValidSearchStrings")
    void testSpvSearchEntity_WithValidSearchString_ShouldReturnResults(String searchString, String description) {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString(searchString);
        SearchResult searchResult = new SearchResult();

        // Redis and JWT stubs - service expects these during the search flow
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        // Ensure JWT secret and serialization are available so generateRedisJwtTokenKey doesn't fail
        lenient().when(cbServerProperties.getJwtSecretKey()).thenReturn("test-secret-key");
        lenient().when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);
        // The service uses objectMapper.writeValueAsString internally to create the JWT claim - mock it
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // won't happen - mock
        }

        // Stub ES util to return a SearchResult
        when(esUtilService.searchDocumentsV2(eq(Constants.KNOWLEDGE_CENTRE_INDEX_NAME), any(SearchCriteria.class)))
                .thenReturn(searchResult);

        // Ensure objectMapper and user service conversions return expected shapes
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(Constants.DATA, new ArrayList<>());
        when(objectMapper.convertValue(eq(searchResult), any(TypeReference.class))).thenReturn(jsonMap);
        List<Object> userList = Collections.emptyList();
        lenient().when(userService.fetchUserFromPrimary(anyList())).thenReturn(userList);
        lenient().when(objectMapper.convertValue(eq(userList), any(TypeReference.class))).thenReturn(userList);

        // Act
        ApiResponse response = knowledgeService.spvSearchEntity(criteria);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode(),
                "Should return OK for: " + description);
        verify(esUtilService).searchDocumentsV2(eq(Constants.KNOWLEDGE_CENTRE_INDEX_NAME), any(SearchCriteria.class));
    }

    private static Stream<Arguments> provideValidSearchStrings() {
        return Stream.of(
                Arguments.of("test query", "valid search query with multiple words"),
                Arguments.of("ab", "exactly 2 characters (minimum boundary)"),
                Arguments.of(null, "null search string"),
                Arguments.of("test", "simple search term")
        );
    }

    /**
     * Parameterized test for invalid search scenarios that should return BAD_REQUEST
     */
    @ParameterizedTest(name = "Search with searchString=''{0}'' should return BAD_REQUEST")
    @MethodSource("provideInvalidSearchStrings")
    void testSpvSearchEntity_WithInvalidSearchString_ShouldReturnBadRequest(String searchString, String description) {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString(searchString);

        // Act
        ApiResponse response = knowledgeService.spvSearchEntity(criteria);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode(),
                "Should return BAD_REQUEST for: " + description);
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        if (searchString != null && !searchString.isEmpty()) {
            assertEquals(Constants.SEARCH_MIN_LENGTH_ERROR_MESSAGE, response.getParams().getErrMsg());
        }
        verify(esUtilService, never()).searchDocumentsV2(anyString(), any());
    }

    private static Stream<Arguments> provideInvalidSearchStrings() {
        return Stream.of(
                Arguments.of("a", "single character (less than minimum 2)"),
                Arguments.of("", "empty string"),
                Arguments.of("x", "single character 'x'")
        );
    }

    @Test
    void testSpvSearchEntity_WithException_ShouldReturnInternalServerError() {
        // Arrange
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("test");

        // Ensure JWT secret and serialization are available so JWT generation doesn't throw
        lenient().when(cbServerProperties.getJwtSecretKey()).thenReturn("test-secret-key");
        lenient().when(cbServerProperties.getSearchResultRedisTtl()).thenReturn(3600L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        try {
            lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // won't happen - mock
        }

        when(esUtilService.searchDocumentsV2(eq(Constants.KNOWLEDGE_CENTRE_INDEX_NAME), any(SearchCriteria.class)))
                .thenThrow(new RuntimeException("Search error"));

        // Act
        ApiResponse response = knowledgeService.spvSearchEntity(criteria);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertNotNull(response.getParams().getErrMsg());
    }

    private JsonNode createValidCategoryNode() {
        ObjectNode node = realObjectMapper.createObjectNode();
        node.put("title", "Test Category");
        node.put("summary", "Test summary");
        node.put("type", Constants.CATEGORY);
        node.put("isPublic", true);
        return node;
    }

    private JsonNode createValidSubCategoryNode() {
        ObjectNode node = realObjectMapper.createObjectNode();
        node.put("title", "Test SubCategory");
        node.put("categoryId", "parent-category-id");
        node.put("type", Constants.SUBCATEGORY);
        return node;
    }

    private JsonNode createValidArticleNode() {
        ObjectNode node = realObjectMapper.createObjectNode();
        node.put("title", "Test Article");
        node.put("subCategoryId", "parent-subcategory-id");
        node.put("type", Constants.ARTICLE);
        node.put("content", "Article content");
        return node;
    }

    private KnowledgeCategoryEntity createMockCategoryEntity() {
        KnowledgeCategoryEntity entity = new KnowledgeCategoryEntity();
        entity.setCategoryId(entityId);
        ObjectNode data = realObjectMapper.createObjectNode();
        data.put("title", "Test Category");
        data.put("createdBy", userId);
        data.put("createdOn", "2026-02-23T10:00:00+05:30");
        entity.setCategoryData(data);
        entity.setCreatedOn("2026-02-23T10:00:00+05:30");
        entity.setUpdatedOn("2026-02-23T10:00:00+05:30");
        return entity;
    }

    private KnowledgeSubCategoryEntity createMockSubCategoryEntity() {
        KnowledgeSubCategoryEntity entity = new KnowledgeSubCategoryEntity();
        entity.setSubCategoryId(entityId);
        ObjectNode data = realObjectMapper.createObjectNode();
        data.put("title", "Test SubCategory");
        data.put("createdBy", userId);
        data.put("createdOn", "2026-02-23T10:00:00+05:30");
        entity.setSubCategoryData(data);
        entity.setCreatedOn("2026-02-23T10:00:00+05:30");
        entity.setUpdatedOn("2026-02-23T10:00:00+05:30");
        return entity;
    }

    private KnowledgeArticleEntity createMockArticleEntity() {
        KnowledgeArticleEntity entity = new KnowledgeArticleEntity();
        entity.setArticleId(entityId);
        ObjectNode data = realObjectMapper.createObjectNode();
        data.put("title", "Test Article");
        data.put("createdBy", userId);
        data.put("createdOn", "2026-02-23T10:00:00+05:30");
        entity.setArticles(data);
        entity.setCreatedOn("2026-02-23T10:00:00+05:30");
        entity.setUpdatedOn("2026-02-23T10:00:00+05:30");
        return entity;
    }
}