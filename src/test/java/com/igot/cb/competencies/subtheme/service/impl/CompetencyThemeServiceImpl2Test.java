package com.igot.cb.competencies.subtheme.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.competencies.theme.enity.CompetencyThemeEntity;
import com.igot.cb.competencies.theme.repository.CompetencyThemeRepository;
import com.igot.cb.competencies.theme.service.impl.CompetencyThemeServiceImpl;
import com.igot.cb.designation.service.DesignationService;
import com.igot.cb.pores.Service.OutboundRequestHandlerServiceImpl;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyThemeServiceImpl2Test {

    @InjectMocks
    private CompetencyThemeServiceImpl competencyThemeService;

    @Mock
    private ObjectMapper objectMapper;
    @Mock private PayloadValidation payloadValidation;
    @Mock private EsUtilService esUtilService;
    @Mock private CacheService cacheService;
    @Mock private CbServerProperties cbServerProperties;
    @Mock private FileProcessService fileProcessService;
    @Mock private AccessTokenValidator accessTokenValidator;
    @Mock private RedisTemplate<String, SearchResult> redisTemplate;
    @Mock private CompetencyThemeRepository competencyThemeRepository;
    @Spy
    private CompetencyThemeServiceImpl spyCompetencyThemeService;

    @Mock private OutboundRequestHandlerServiceImpl outboundRequestHandlerServiceImpl;
    @Mock private DesignationService designationService;

    private static final String TOKEN = "valid-token";
    @Mock private MultipartFile file;
    private final String refId = "comp123";
    private final String name = "Sample Term";
    private final String framework = "framework1";
    private final String category = "category1";
    private final String parentCategory = "parent1";
    private final String prevTermCode = "term001";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(competencyThemeService, "searchResultRedisTtl", 3600L);
        ReflectionTestUtils.setField(competencyThemeService, "objectMapper", new ObjectMapper());
    }

    @Test
    void testLoadCompetencyTheme_Success() throws Exception {
        String token = "mock-token";
        String userId = "user-123";

        // Token validation
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        // ES check
        when(esUtilService.isIndexPresent(Constants.COMP_THEME_INDEX_NAME)).thenReturn(true);

        // Existing titles
        JsonNode existingData = new ObjectMapper().readTree("[{\"title\":\"existing theme\"}]");
        SearchResult searchResult = new SearchResult();
        searchResult.setData(existingData);
        when(esUtilService.searchDocuments(eq(Constants.COMP_THEME_INDEX_NAME), any())).thenReturn(searchResult);

        // Excel data
        List<Map<String, String>> excelData = List.of(
                Map.of(Constants.COMPETENCY_THEME_TYPE, "New Theme", Constants.DESCRIPTION, "Some desc")
        );
        when(fileProcessService.processExcelFile(file)).thenReturn(excelData);

        // Count in DB
        when(competencyThemeRepository.count()).thenReturn(1L);

        // JsonNode conversion
        JsonNode inputNode = new ObjectMapper().valueToTree(excelData);

        // Required mocks for addDocument + saveAll
        when(cbServerProperties.getElasticCompJsonPath()).thenReturn("path/to/schema.json");

        // Execute
        competencyThemeService.loadCompetencyTheme(file, token);

        // Verify
        verify(competencyThemeRepository, atLeastOnce()).saveAll(anyList());
        verify(esUtilService, atLeastOnce()).addDocument(any(), any(), any(), any(), any());
        verify(cacheService, atLeastOnce()).putCache(any(), any());
    }

    private JsonNode mockInputNode(String title) {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(Constants.TITLE, title);
        return node;
    }

    @Test
    void testCreateCompTheme_success() throws Exception {
        JsonNode input = mockInputNode("Theme A");
        String userId = "user123";

        when(esUtilService.isIndexPresent(Constants.COMP_THEME_INDEX_NAME)).thenReturn(true);

        ObjectNode existingData = new ObjectMapper().createObjectNode();
        ArrayNode arrayNode = new ObjectMapper().createArrayNode();
        ObjectNode nodeWithTitle = new ObjectMapper().createObjectNode();
        nodeWithTitle.put(Constants.TITLE, "Theme B");
        arrayNode.add(nodeWithTitle);
        existingData.set("data", arrayNode);
        SearchResult result = new SearchResult();
        result.setData(arrayNode);
        when(esUtilService.searchDocuments(anyString(), any())).thenReturn(result);

        when(accessTokenValidator.verifyUserToken(TOKEN)).thenReturn(userId);
        when(competencyThemeRepository.count()).thenReturn(5L);
        when(cbServerProperties.getElasticCompJsonPath()).thenReturn("path");

        ArgumentCaptor<CompetencyThemeEntity> captor = ArgumentCaptor.forClass(CompetencyThemeEntity.class);

        //when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        CustomResponse response = competencyThemeService.createCompTheme(input, TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(competencyThemeRepository).save(captor.capture());
        verify(esUtilService).addDocument(anyString(), anyString(), anyString(), any(), anyString());
        verify(cacheService).putCache(anyString(), any(JsonNode.class));
    }

    @Test
    void testCreateCompTheme_titleAlreadyExists() throws Exception {
        JsonNode input = mockInputNode("Theme A");

        ArrayNode arrayNode = new ObjectMapper().createArrayNode();
        ObjectNode existing = new ObjectMapper().createObjectNode();
        existing.put(Constants.TITLE, "Theme A");
        arrayNode.add(existing);
        SearchResult result = new SearchResult();
        result.setData(arrayNode);

        when(esUtilService.isIndexPresent(Constants.COMP_THEME_INDEX_NAME)).thenReturn(true);
        when(esUtilService.searchDocuments(anyString(), any())).thenReturn(result);
        when(accessTokenValidator.verifyUserToken(TOKEN)).thenReturn("user123");

        CustomResponse response = competencyThemeService.createCompTheme(input, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Already Present", response.getParams().getErrmsg());
    }

    @Test
    void testCreateCompTheme_invalidToken() {
        JsonNode input = mockInputNode("Theme A");

        when(accessTokenValidator.verifyUserToken(TOKEN)).thenReturn(Constants.UNAUTHORIZED);

        CustomResponse response = competencyThemeService.createCompTheme(input, TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrmsg());
    }

    @Test
    void testCreateCompTheme_esSearchError() throws Exception {
        JsonNode input = mockInputNode("Theme A");

        when(esUtilService.isIndexPresent(Constants.COMP_THEME_INDEX_NAME)).thenReturn(true);
        when(esUtilService.searchDocuments(any(), any()))
                .thenThrow(new RuntimeException("ES failure"));

        Exception exception = assertThrows(CustomException.class,
                () -> competencyThemeService.createCompTheme(input, TOKEN));

        assertEquals("ES failure", exception.getMessage());
    }

 @Test
    void testUpdateCompTheme_success() {
        // given
        String id = "COMTHEME-000001";
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode input = mapper.createObjectNode();
        input.put(Constants.ID, id);
        input.put("title", "updated-title");

        ObjectNode existingData = mapper.createObjectNode();
        existingData.put("title", "old-title");

        CompetencyThemeEntity existingEntity = new CompetencyThemeEntity();
        existingEntity.setId(id);
        existingEntity.setData(existingData);
        existingEntity.setUpdatedOn(new Timestamp(System.currentTimeMillis()));

        when(competencyThemeRepository.findById(id)).thenReturn(Optional.of(existingEntity));
        when(competencyThemeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(cbServerProperties.getElasticCompJsonPath()).thenReturn("dummy/path");

        // when
        CustomResponse response = competencyThemeService.updateCompTheme(input);

        // then
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_UPDATED, response.getMessage());
        verify(esUtilService).updateDocument(eq(Constants.COMP_THEME_INDEX_NAME), eq(Constants.INDEX_TYPE),
                eq(id), anyMap(), anyString());
        verify(cacheService).putCache(eq(id), any());
    }

    @Test
    void testUpdateCompTheme_idMissing() {
        // given
        ObjectNode input = new ObjectMapper().createObjectNode(); // No ID

        // when
        CustomResponse response = competencyThemeService.updateCompTheme(input);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Id is missing", response.getMessage());
    }

    @Test
    void testUpdateCompTheme_noEntityFound() {
        // given
        String id = "COMTHEME-000001";
        ObjectNode input = new ObjectMapper().createObjectNode();
        input.put(Constants.ID, id);

        when(competencyThemeRepository.findById(id)).thenReturn(Optional.empty());

        // when
        CustomResponse response = competencyThemeService.updateCompTheme(input);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("No data found for this id", response.getMessage());
    }

    @Test
    void testUpdateCompTheme_exceptionFlow() {
        // given
        String id = "COMTHEME-000001";
        ObjectNode input = new ObjectMapper().createObjectNode();
        input.put(Constants.ID, id);

        when(competencyThemeRepository.findById(id)).thenThrow(new RuntimeException("DB failure"));

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> competencyThemeService.updateCompTheme(input));
        assertEquals("DB failure", ex.getMessage());
    }

    @Test
    void testCreateTerm_TermAlreadyExistsConflict() throws Exception {
        JsonNode request = createRequestNode();
        CompetencyThemeEntity entity = new CompetencyThemeEntity();
        entity.setIsActive(true);
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.of(entity));

        ApiResponse conflictResponse = new ApiResponse();
        conflictResponse.setResponseCode(HttpStatus.CONFLICT);
        when(designationService.frameworkRead(framework, parentCategory, prevTermCode, refId)).thenReturn(conflictResponse);

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Term creation failed. A term with the reference ID already exists. Please use a unique reference ID.comp123", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_InternalServerError() throws Exception {
        JsonNode request = createRequestNode();
        CompetencyThemeEntity entity = new CompetencyThemeEntity();
        entity.setIsActive(true);
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.of(entity));

        ApiResponse notFoundResponse = new ApiResponse();
        notFoundResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(designationService.frameworkRead(framework, parentCategory, prevTermCode, refId)).thenReturn(notFoundResponse);

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put("responseCode", "OK");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("node_id", List.of("term123"));
        termResponse.put("result", resultMap);

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://mockurl/");
        when(cbServerProperties.getOdcsTermCrete()).thenReturn("create");

        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), any())).thenReturn(termResponse);

        // Simulate update success
        CustomResponse customResponse = new CustomResponse();
        customResponse.setResponseCode(HttpStatus.OK);

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
    }

    @Test
    void testCreateTerm_InactiveEntity() throws Exception {
        JsonNode request = createRequestNode();
        CompetencyThemeEntity entity = new CompetencyThemeEntity();
        entity.setIsActive(false);
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.of(entity));

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to create term.", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_FrameworkReadReturnsNull() throws Exception {
        JsonNode request = createRequestNode();
        CompetencyThemeEntity entity = new CompetencyThemeEntity();
        entity.setIsActive(true);
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.of(entity));
        when(designationService.frameworkRead(framework, parentCategory, prevTermCode, refId)).thenReturn(null);

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to validate term exists or not.", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_TermCreationFails() throws Exception {
        JsonNode request = createRequestNode();
        CompetencyThemeEntity entity = new CompetencyThemeEntity();
        entity.setIsActive(true);
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.of(entity));

        ApiResponse notFoundResponse = new ApiResponse();
        notFoundResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(designationService.frameworkRead(framework, parentCategory, prevTermCode, refId)).thenReturn(notFoundResponse);

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://mockurl/");
        when(cbServerProperties.getOdcsTermCrete()).thenReturn("create");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), any())).thenReturn(null);

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to create the term", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_EntityNotFound() throws Exception {
        JsonNode request = createRequestNode();
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenReturn(Optional.empty());

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("term Not Exist.", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_UnexpectedException() throws Exception {
        JsonNode request = createRequestNode();
        when(competencyThemeRepository.findByIdAndIsActive(refId, true)).thenThrow(new RuntimeException("DB down"));

        ApiResponse response = competencyThemeService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Unexpected error occurred while processing the request.", response.getParams().getErr());
    }

    @Test
    void deleteCompetencyTheme_shouldReturnInternalServerError_whenExceptionIsThrown() {
        // Arrange
        String id = "theme123";

        // Simulate exception
        when(competencyThemeRepository.findByIdAndIsActive(id, true))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act
        CustomResponse response = competencyThemeService.deleteCompetencyTheme(id);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Database connection failed", response.getMessage());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    private JsonNode createRequestNode() {
        ObjectNode additionalProps = new ObjectMapper().createObjectNode();
        additionalProps.put(Constants.PARENT_CATEGORY, parentCategory);
        additionalProps.put(Constants.PREV_TERM_CODE, prevTermCode);

        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put(Constants.NAME, name);
        node.put(Constants.REF_ID, refId);
        node.put(Constants.FRAMEWORK, framework);
        node.put(Constants.CATEGORY, category);
        node.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);
        return node;
    }

}