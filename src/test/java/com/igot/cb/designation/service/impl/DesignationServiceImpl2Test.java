package com.igot.cb.designation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.designation.entity.DesignationEntity;
import com.igot.cb.designation.repository.DesignationRepository;
import com.igot.cb.pores.Service.OutboundRequestHandlerServiceImpl;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DesignationServiceImpl2Test {
    @Spy
    @InjectMocks
    private DesignationServiceImpl designationService;

    @Mock
    private DesignationRepository designationRepository;
    @Mock private PayloadValidation payloadValidation;
    @Mock private EsUtilService esUtilService;
    @Mock private CacheService cacheService;
    @Mock private CbServerProperties cbServerProperties;
    @Mock private OutboundRequestHandlerServiceImpl outboundRequestHandlerServiceImpl;
    @Mock private RedisTemplate<String, SearchResult> redisTemplate;
    @Mock private AccessTokenValidator accessTokenValidator;
    @Mock private ObjectMapper objectMapper;
    private static final String TOKEN = "dummy-token";

    @Test
    void testValidateFileAndProcessRows_xlsx() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Designation");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("Developer");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        MultipartFile file = new MockMultipartFile("file", "input.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());

        Method method = DesignationServiceImpl.class.getDeclaredMethod("validateFileAndProcessRows", MultipartFile.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>) method.invoke(designationService, file);

        assertEquals(1, result.size());
        assertEquals("Developer", result.get(0).get("Designation"));
    }

    @Test
    void testProcessCsvAndSendMessage() throws Exception {
        String csv = "Designation\nEngineer\n";
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        Method method = DesignationServiceImpl.class.getDeclaredMethod("processCsvAndSendMessage", InputStream.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>) method.invoke(designationService, stream);

        assertEquals(1, result.size());
        assertEquals("Engineer", result.get(0).get("Designation"));
    }

    @Test
    void testProcessSheetAndSendMessage() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Designation");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("Analyst");

        Method method = DesignationServiceImpl.class.getDeclaredMethod("processSheetAndSendMessage", Sheet.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>) method.invoke(designationService, sheet);

        assertEquals(1, result.size());
        assertEquals("Analyst", result.get(0).get("Designation"));
    }

    @Test
    void testLoadDesignation_Success() throws Exception {
        when(designationRepository.count()).thenReturn(0L);
        when(accessTokenValidator.fetchUserIdFromAccessToken(eq(TOKEN), any(ApiResponse.class)))
                .thenReturn("user-1");
        when(esUtilService.isIndexPresent(Constants.DESIGNATION_INDEX_NAME)).thenReturn(true);
        JsonNode mockEsData = new ObjectMapper().createArrayNode();
        SearchResult searchResult = new SearchResult();
        searchResult.setData(mockEsData);
        when(esUtilService.searchDocuments(eq(Constants.DESIGNATION_INDEX_NAME), any())).thenReturn(searchResult);
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Designation");
        Row data = sheet.createRow(1);
        data.createCell(0).setCellValue("Developer");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        MockMultipartFile mockExcelFile = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray()
        );
        ObjectMapper realMapper = new ObjectMapper();
        JsonNode excelJson = realMapper.valueToTree(List.of(Map.of("Designation", "Developer")));
        ObjectNode emptyObjNode = realMapper.createObjectNode();
        when(objectMapper.valueToTree(any())).thenReturn(excelJson);
        when(objectMapper.createObjectNode()).thenReturn(emptyObjNode);
        ApiResponse response = designationService.loadDesignation(mockExcelFile, TOKEN);
        assertNotNull(response);
        assertEquals(Constants.API_DESIGNATION_UPLOAD, response.getId());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus()); // Assuming default status is SUCCESS
        assertNull(response.getParams().getErrMsg());
    }


    @Test
    void testCreateTerm_PayloadValidationFails() throws Exception {
        JsonNode invalidRequest = new ObjectMapper().createObjectNode();

        doThrow(new CustomException("Invalid payload" , "", HttpStatus.BAD_REQUEST))
                .when(payloadValidation).validatePayload(anyString(), any());

        ApiResponse response = designationService.createTerm(invalidRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateTerm_DesignationExistsButInactive() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode();
        ObjectNode additionalProps = mapper.createObjectNode();
        additionalProps.put(Constants.PARENT_CATEGORY, "parentCat");
        additionalProps.put(Constants.PREV_TERM_CODE, "termCode");
        request.put(Constants.NAME, "Designation1");
        request.put(Constants.REF_ID, "ref123");
        request.put(Constants.FRAMEWORK, "framework1");
        request.put(Constants.CATEGORY, "category1");
        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity inactiveDesg = new DesignationEntity();
        inactiveDesg.setIsActive(false);

        when(designationRepository.findByIdAndIsActive("ref123", true))
                .thenReturn(Optional.of(inactiveDesg));

        ApiResponse response = designationService.createTerm(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to create Designation.", response.getParams().getErr());
    }


    @Test
    void testCreateTerm_success() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode()
                .put(Constants.NAME, "Software Engineer")
                .put(Constants.REF_ID, "REF123")
                .put(Constants.FRAMEWORK, "FW123")
                .put(Constants.CATEGORY, "designation");

        ObjectNode additionalProps = mapper.createObjectNode()
                .put(Constants.PARENT_CATEGORY, "parent")
                .put(Constants.PREV_TERM_CODE, "term123");

        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity designationEntity = new DesignationEntity();
        designationEntity.setIsActive(true);
        designationEntity.setId("REF123");
        designationEntity.setData(mapper.createObjectNode()); // Required to avoid NPE in some flows

        when(designationRepository.findByIdAndIsActive("REF123", true)).thenReturn(Optional.of(designationEntity));

        // Mock frameworkRead to return NOT_FOUND
        ApiResponse mockReadResponse = new ApiResponse();
        mockReadResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(designationService.frameworkRead("FW123", "parent", "term123", "REF123")).thenReturn(mockReadResponse);

        // Mock outbound request response
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, List.of("term_001"));

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        termResponse.put(Constants.RESULT, resultMap);

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsTermCrete()).thenReturn("create/term");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap()))
                .thenReturn(termResponse);

        // IMPORTANT: updateIdentifiersToDesignation must not return null
        CustomResponse mockUpdateResponse = new CustomResponse();
        mockUpdateResponse.setResponseCode(HttpStatus.OK);

        // You MUST either:
        // a) Use doReturn() style for a real spy
        // OR
        // b) Make updateIdentifiersToDesignation protected/package-private if mocking
        doReturn(mockUpdateResponse).when(designationService).updateIdentifiersToDesignation(any());

        //doReturn(mockUpdateResponse).when(designationService).updateIdentifiersToDesignation(any(JsonNode.class));

        // Act
        ApiResponse response = designationService.createTerm(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.NODE_ID));
        assertEquals(List.of("term_001"), response.getResult().get(Constants.NODE_ID));
    }


    @Test
    void testCreateTerm_whenUpdateFailed() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode()
                .put(Constants.NAME, "Software Engineer")
                .put(Constants.REF_ID, "REF123")
                .put(Constants.FRAMEWORK, "FW123")
                .put(Constants.CATEGORY, "designation");

        ObjectNode additionalProps = mapper.createObjectNode()
                .put(Constants.PARENT_CATEGORY, "parent")
                .put(Constants.PREV_TERM_CODE, "term123");

        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity designationEntity = new DesignationEntity();
        designationEntity.setIsActive(true);
        designationEntity.setId("REF123");
        designationEntity.setData(mapper.createObjectNode()); // Required to avoid NPE in some flows

        when(designationRepository.findByIdAndIsActive("REF123", true)).thenReturn(Optional.of(designationEntity));

        // Mock frameworkRead to return NOT_FOUND
        ApiResponse mockReadResponse = new ApiResponse();
        mockReadResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(designationService.frameworkRead("FW123", "parent", "term123", "REF123")).thenReturn(mockReadResponse);

        // Mock outbound request response
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, List.of("term_001"));

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        termResponse.put(Constants.RESULT, resultMap);

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsTermCrete()).thenReturn("create/term");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap()))
                .thenReturn(termResponse);

        // IMPORTANT: updateIdentifiersToDesignation must not return null
        CustomResponse mockUpdateResponse = new CustomResponse();
        mockUpdateResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        // You MUST either:
        // a) Use doReturn() style for a real spy
        // OR
        // b) Make updateIdentifiersToDesignation protected/package-private if mocking
        doReturn(mockUpdateResponse).when(designationService).updateIdentifiersToDesignation(any());

        // Act
        ApiResponse response = designationService.createTerm(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to update designation.", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_FailedToCreateDesignation() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode()
                .put(Constants.NAME, "Software Engineer")
                .put(Constants.REF_ID, "REF123")
                .put(Constants.FRAMEWORK, "FW123")
                .put(Constants.CATEGORY, "designation");

        ObjectNode additionalProps = mapper.createObjectNode()
                .put(Constants.PARENT_CATEGORY, "parent")
                .put(Constants.PREV_TERM_CODE, "term123");

        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity designationEntity = new DesignationEntity();
        designationEntity.setIsActive(true);
        designationEntity.setId("REF123");
        designationEntity.setData(mapper.createObjectNode()); // Required to avoid NPE in some flows

        when(designationRepository.findByIdAndIsActive("REF123", true)).thenReturn(Optional.of(designationEntity));

        // Mock frameworkRead to return NOT_FOUND
        ApiResponse mockReadResponse = new ApiResponse();
        mockReadResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(designationService.frameworkRead("FW123", "parent", "term123", "REF123")).thenReturn(mockReadResponse);

        // Mock outbound request response
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, List.of("term_001"));

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put(Constants.RESPONSE_CODE, Constants.NOT_FOUND);

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsTermCrete()).thenReturn("create/term");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap()))
                .thenReturn(termResponse);
        // Act
        ApiResponse response = designationService.createTerm(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to create the Designation", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_WhenFrameworkReadNull() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode request = mapper.createObjectNode()
                .put(Constants.NAME, "Software Engineer")
                .put(Constants.REF_ID, "REF123")
                .put(Constants.FRAMEWORK, "FW123")
                .put(Constants.CATEGORY, "designation");

        ObjectNode additionalProps = mapper.createObjectNode()
                .put(Constants.PARENT_CATEGORY, "parent")
                .put(Constants.PREV_TERM_CODE, "term123");

        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity designationEntity = new DesignationEntity();
        designationEntity.setIsActive(true);
        designationEntity.setId("REF123");
        designationEntity.setData(mapper.createObjectNode()); // Required to avoid NPE in some flows

        when(designationRepository.findByIdAndIsActive("REF123", true)).thenReturn(Optional.of(designationEntity));

        // Mock frameworkRead to return null
        when(designationService.frameworkRead("FW123", "parent", "term123", "REF123")).thenReturn(null);

        // Mock outbound request response
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, List.of("term_001"));

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put(Constants.RESPONSE_CODE, Constants.NOT_FOUND);

        // Act
        ApiResponse response = designationService.createTerm(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to validate sector exists or not.", response.getParams().getErr());
    }

    @Test
    void testCreateTerm_WhenFrameworkReadReturnConflict() throws Exception {
        // Arrange
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode request = mapper.createObjectNode()
                .put(Constants.NAME, "Software Engineer")
                .put(Constants.REF_ID, "REF123")
                .put(Constants.FRAMEWORK, "FW123")
                .put(Constants.CATEGORY, "designation");

        ObjectNode additionalProps = mapper.createObjectNode()
                .put(Constants.PARENT_CATEGORY, "parent")
                .put(Constants.PREV_TERM_CODE, "term123");

        request.set(Constants.ADDITIONAL_PROPERTIES, additionalProps);

        DesignationEntity designationEntity = new DesignationEntity();
        designationEntity.setIsActive(true);
        designationEntity.setId("REF123");
        designationEntity.setData(mapper.createObjectNode()); // Required to avoid NPE in some flows

        when(designationRepository.findByIdAndIsActive("REF123", true)).thenReturn(Optional.of(designationEntity));

        // Mock frameworkRead to return Conflict
        ApiResponse mockReadResponse = new ApiResponse();
        mockReadResponse.setResponseCode(HttpStatus.CONFLICT);
        when(designationService.frameworkRead("FW123", "parent", "term123", "REF123")).thenReturn(null);

        // Mock outbound request response
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, List.of("term_001"));

        Map<String, Object> termResponse = new HashMap<>();
        termResponse.put(Constants.RESPONSE_CODE, Constants.NOT_FOUND);

        // Act
        ApiResponse response = designationService.createTerm(request);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Failed to validate sector exists or not.", response.getParams().getErr());
    }

}

