package com.igot.cb.contentpartner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.contentpartner.service.ContentPartnerRegistrationService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentPartnerRegistrationControllerTest {

    @Mock
    private ContentPartnerRegistrationService partnerService;

    @InjectMocks
    private ContentPartnerRegistrationController controller;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String token = "dummy-token";

    @Test
    void testCreate_Success() throws Exception {
        JsonNode requestJson = mapper.readTree(
                "{\"contentPartnerName\":\"Org1\",\"email\":\"org@gmail.com\"}"
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("id", "generated-id-123");
        result.put("status", Constants.PENDING);
        mockResponse.setResult(result);

        when(partnerService.insert(any(JsonNode.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.create(requestJson);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).insert(any(JsonNode.class));
    }

    @Test
    void testCreate_Failure() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"contentPartnerName\":\"Org1\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Email is required");

        when(partnerService.insert(any(JsonNode.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.create(requestJson);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).insert(any(JsonNode.class));
    }

    @Test
    void testCreate_OrgNameAlreadyExists() throws Exception {
        JsonNode requestJson = mapper.readTree(
                "{\"contentPartnerName\":\"ExistingOrg\",\"email\":\"new@gmail.com\"}"
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Organization Name already registered");

        when(partnerService.insert(any(JsonNode.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.create(requestJson);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).insert(any(JsonNode.class));
    }

    @Test
    void testCreate_EmailAlreadyExists() throws Exception {
        JsonNode requestJson = mapper.readTree(
                "{\"contentPartnerName\":\"NewOrg\",\"email\":\"existing@gmail.com\"}"
        );

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Email already registered");

        when(partnerService.insert(any(JsonNode.class))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.create(requestJson);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).insert(any(JsonNode.class));
    }

    @Test
    void testUpdate_Success() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"id\":\"123\",\"status\":\"APPROVED\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("id", "123");
        result.put("status", Constants.APPROVED);
        mockResponse.setResult(result);

        when(partnerService.update(any(JsonNode.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.update(requestJson, token);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).update(any(JsonNode.class), eq(token));
    }

    @Test
    void testUpdate_Rejected() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"id\":\"456\",\"status\":\"REJECTED\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("id", "456");
        result.put("status", Constants.REJECTED);
        mockResponse.setResult(result);

        when(partnerService.update(any(JsonNode.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.update(requestJson, token);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).update(any(JsonNode.class), eq(token));
    }

    @Test
    void testUpdate_InvalidStatus() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"id\":\"123\",\"status\":\"INVALID\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Invalid status. Allowed values: APPROVED, REJECTED");

        when(partnerService.update(any(JsonNode.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.update(requestJson, token);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).update(any(JsonNode.class), eq(token));
    }

    @Test
    void testUpdate_Unauthorized() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"id\":\"123\",\"status\":\"APPROVED\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        mockResponse.getParams().setErrMsg(Constants.UNAUTHORIZED);

        when(partnerService.update(any(JsonNode.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.update(requestJson, token);

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).update(any(JsonNode.class), eq(token));
    }

    @Test
    void testUpdate_NotFound() throws Exception {
        JsonNode requestJson = mapper.readTree("{\"id\":\"non-existent\",\"status\":\"APPROVED\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.NOT_FOUND);
        mockResponse.getParams().setErrMsg("Content Partner Registration not found");

        when(partnerService.update(any(JsonNode.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.update(requestJson, token);

        assertEquals(HttpStatus.NOT_FOUND, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).update(any(JsonNode.class), eq(token));
    }

    @Test
    void testReadById_Success() throws Exception {
        String id = "test-id-123";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("contentPartnerName", "Org1");
        result.put("email", "org1@gmail.com");
        result.put("status", Constants.APPROVED);
        mockResponse.setResult(result);
        when(partnerService.read(eq(id), isNull())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.read(id, null);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).read(eq(id), isNull());
    }

    @Test
    void testReadById_NotFound() throws Exception {
        String id = "non-existent-id";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg(Constants.INVALID_ID);
        when(partnerService.read(eq(id), isNull())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.read(id, null);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).read(eq(id), isNull());
    }

    @Test
    void testReadByEmail_Success() throws Exception {
        String email = "org1@gmail.com";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("contentPartnerName", "Org1");
        mockResponse.setResult(result);
        when(partnerService.read(isNull(), eq(email))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.read(null, email);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).read(isNull(), eq(email));
    }

    @Test
    void testRead_NoParams_BadRequest() throws Exception {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Either id or email must be provided");
        when(partnerService.read(isNull(), isNull())).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.read(null, null);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).read(isNull(), isNull());
    }

    @Test
    void testSearch_Success() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("Org");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        Map<String, Object> result = new HashMap<>();
        result.put("total", 5);
        mockResponse.setResult(result);

        when(partnerService.searchEntity(any(SearchCriteria.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.search(criteria, token);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).searchEntity(any(SearchCriteria.class), eq(token));
    }

    @Test
    void testSearch_MinCharactersValidation() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("ab");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Minimum 3 characters are required to search");

        when(partnerService.searchEntity(any(SearchCriteria.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.search(criteria, token);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).searchEntity(any(SearchCriteria.class), eq(token));
    }

    @Test
    void testSearch_Unauthorized() throws Exception {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSearchString("Org");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        mockResponse.getParams().setErrMsg(Constants.UNAUTHORIZED);

        when(partnerService.searchEntity(any(SearchCriteria.class), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.search(criteria, token);

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).searchEntity(any(SearchCriteria.class), eq(token));
    }

    @Test
    void testReadById_MissingId() throws Exception {
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Id is required");

        when(partnerService.readById(isNull(), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.readById(null, token);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).readById(isNull(), eq(token));
    }

    @Test
    void testReadById_Unauthorized() throws Exception {
        String id = "test-id-123";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        mockResponse.getParams().setErrMsg(Constants.UNAUTHORIZED);

        when(partnerService.readById(eq(id), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.readById(id, token);

        assertEquals(HttpStatus.UNAUTHORIZED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).readById(eq(id), eq(token));
    }

    @Test
    void testReadById_EmptyId() throws Exception {
        String id = "";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        mockResponse.getParams().setErrMsg("Id cannot be empty");

        when(partnerService.readById(eq(id), eq(token))).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = controller.readById(id, token);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
        verify(partnerService, times(1)).readById(eq(id), eq(token));
    }
}