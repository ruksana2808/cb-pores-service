package com.igot.cb.contentpartner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.contentpartner.service.ContentPartnerService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentPartnerControllerTest {

    @InjectMocks
    private ContentPartnerController contentPartnerController;

    @Mock
    private ContentPartnerService partnerService;

    /**
     * Test case for deleting a content partner.
     * This test verifies that the delete method in ContentPartnerController
     * correctly calls the partnerService.delete method and returns the expected
     * ResponseEntity with OK status.
     */
    @Test
    void testDeleteContentPartner() {
        String id = "test-id";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(partnerService.delete(id)).thenReturn(mockResponse);

        ResponseEntity<?> responseEntity = contentPartnerController.delete(id);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    /**
     * Test case for the read method in ContentPartnerController.
     * This test verifies that the read method returns a ResponseEntity with an ApiResponse
     * and HTTP status OK when given a valid content partner ID.
     */
    @Test
    void testReadContentPartnerById() {
        String id = "validPartnerId";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(partnerService.read(id)).thenReturn(mockResponse);

        ResponseEntity<?> result = contentPartnerController.read(id);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Test case for the search method in ContentPartnerController.
     * It verifies that the search method correctly processes the SearchCriteria
     * and returns the expected ResponseEntity with the ApiResponse.
     */
    @Test
    void testSearchReturnsCorrectResponseEntity() {
        // Arrange
        SearchCriteria searchCriteria = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(partnerService.searchEntity(searchCriteria)).thenReturn(mockResponse);

        // Act
        ResponseEntity<?> result = contentPartnerController.search(searchCriteria);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    /**
     * Test the update method of ContentPartnerController
     * Verifies that the method returns the correct ResponseEntity with ApiResponse
     * when given valid content partner details
     */
    @Test
    void testUpdateContentPartner() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode contentPartnerDetails = objectMapper.createObjectNode().put("name", "Test Partner");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(partnerService.createOrUpdate(contentPartnerDetails)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = contentPartnerController.update(contentPartnerDetails);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    /**
     * Tests the update method with an empty JsonNode input.
     * This test verifies that the method handles an empty input correctly.
     */
    @Test
    void testUpdateWithEmptyInput() {
        JsonNode emptyNode = new ObjectMapper().createObjectNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(partnerService.createOrUpdate(emptyNode)).thenReturn(mockResponse);

        ResponseEntity<?> responseEntity = contentPartnerController.update(emptyNode);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
    }

    /**
     * Tests the create method of ContentPartnerController.
     * Verifies that the method correctly calls the service layer and returns the expected response.
     */
    @Test
    void test_create_success() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode contentPartnerDetails = objectMapper.readTree("{\"name\":\"Test Partner\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CREATED);

        when(partnerService.createOrUpdate(contentPartnerDetails)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> responseEntity = contentPartnerController.create(contentPartnerDetails);

        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(mockResponse, responseEntity.getBody());
    }

    /**
     * Test the fetchContentDetailsByName method with an empty partner code.
     * This test verifies that the method handles empty input correctly by returning an appropriate ApiResponse.
     */
    @Test
    void test_fetchContentDetailsByName_emptyPartnerCode() {
        String emptyPartnerCode = "";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);
        when(partnerService.getContentDetailsByPartnerCode(emptyPartnerCode)).thenReturn(mockResponse);

        ResponseEntity<?> result = contentPartnerController.fetchContentDetailsByName(emptyPartnerCode);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    /**
     * Test case for fetchContentDetailsByName method when a valid partner code is provided.
     * It verifies that the method returns the correct ResponseEntity with OK status
     * and the ApiResponse from the service.
     */
    @Test
    void test_fetchContentDetailsByName_validPartnerCode() {
        String partnerCode = "VALID_PARTNER_CODE";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.valueOf(HttpStatus.OK.value()));

        when(partnerService.getContentDetailsByPartnerCode(partnerCode)).thenReturn(mockResponse);

        ResponseEntity<?> result = contentPartnerController.fetchContentDetailsByName(partnerCode);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

}
