package com.igot.cb.playlist.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.playlist.dto.SearchDto;
import com.igot.cb.playlist.service.PlayListSerive;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PlayListController playListController;

    @Mock
    private PlayListSerive playListSerive;

    /**
     * Tests the create method with an empty JsonNode input.
     * This test verifies that the method handles an empty input appropriately,
     * expecting a bad request response.
     */
    @Test
    public void testCreateWithEmptyInput() {
        JsonNode emptyNode = objectMapper.createObjectNode();
        ApiResponse expectedResponse = new ApiResponse();
        expectedResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(playListSerive.createPlayList(emptyNode)).thenReturn(expectedResponse);

        ResponseEntity<?> result = (ResponseEntity<?>) playListController.create(emptyNode);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    /**
     * Test the delete method with an empty ID.
     * This test verifies that when an empty ID is provided,
     * the method returns an appropriate error response.
     */
    @Test
    public void testDeleteWithEmptyId() {
        String emptyId = "";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(playListSerive.delete(emptyId)).thenReturn(mockResponse);

        ResponseEntity<?> result = (ResponseEntity<?>) playListController.delete(emptyId);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Test case for updating a playlist.
     * This test verifies that the update method in PlayListController
     * correctly processes the input JsonNode and returns the expected
     * ResponseEntity with the ApiResponse from the service.
     */
    @Test
    public void testUpdatePlaylist() throws JsonProcessingException {
        // Prepare test data
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode playListDetails = objectMapper.readTree("{\"id\":\"123\",\"name\":\"Updated Playlist\"}");

        // Mock the service response
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.updatePlayList(playListDetails)).thenReturn(mockResponse);

        // Call the method under test
        ResponseEntity<?> result = (ResponseEntity<?>) playListController.update(playListDetails);

        // Verify the result
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    public void test_createV2_1() {
        // Given
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode playListDetails = objectMapper.createObjectNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CREATED);

        // When
        when(playListSerive.createV2PlayList(playListDetails)).thenReturn(mockResponse);

        // Then
        ResponseEntity<?> result = (ResponseEntity<?>) playListController.createV2(playListDetails);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Test case for creating a playlist
     * Verifies that the create method returns the correct ResponseEntity with ApiResponse
     */
    @Test
    public void test_create_playlist() {
        // Prepare test data
        JsonNode playListDetails = new ObjectMapper().createObjectNode().put("name", "Test Playlist");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.CREATED);

        // Mock the service method
        when(playListSerive.createPlayList(any(JsonNode.class))).thenReturn(mockResponse);

        // Call the controller method
        ResponseEntity<?> result = (ResponseEntity<?>) playListController.create(playListDetails);

        // Verify the result
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Tests the delete method of PlayListController.
     * Verifies that the method correctly delegates to the service and returns the expected ResponseEntity.
     */
    @Test
    public void test_delete_successful() {
        String id = "testId";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.delete(id)).thenReturn(mockResponse);

        ResponseEntity<?> result = (ResponseEntity<?>) playListController.delete(id);

        verify(playListSerive).delete(id);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Test case for playListReadV2 method when valid parameters are provided.
     * It verifies that the method returns the expected ResponseEntity with the correct ApiResponse.
     */
    @Test
    public void test_playListReadV2_ValidParameters() {
        // Arrange
        String id = "testId";
        String playListId = "testPlayListId";
        String orgId = "testOrgId";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(playListSerive.readV2Playlist(id, playListId, orgId)).thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.playListReadV2(id, playListId, orgId);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Tests the playListRead method of PlayListController.
     * This test verifies that the method correctly calls the service layer
     * and returns the appropriate ResponseEntity based on the ApiResponse.
     */
    @Test
    public void test_playListRead_returnsCorrectResponseEntity() {
        // Arrange
        SearchDto searchDto = new SearchDto();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(playListSerive.searchPlayListForOrg(any(SearchDto.class))).thenReturn(mockResponse);

        // Act
        ResponseEntity<?> result = (ResponseEntity<?>) playListController.playListRead(searchDto);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Test case for playListSearch method when a valid SearchCriteria is provided.
     * It verifies that the method returns a ResponseEntity with the correct ApiResponse and HTTP status.
     */
    @Test
    public void test_playListSearch_withValidCriteria() {
        // Arrange
        SearchCriteria searchDto = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);
        when(playListSerive.searchPlayList(searchDto)).thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.playListSearch(searchDto);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    /**
     * Tests the updateV2 method with an empty JsonNode input.
     * This test verifies that the method handles an empty input appropriately,
     * expecting a BAD_REQUEST response.
     */
    @Test
    public void test_updateV2_emptyJsonNode() throws JsonProcessingException {
        JsonNode emptyNode = new ObjectMapper().createObjectNode();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.BAD_REQUEST);

        when(playListSerive.updateV2PlayList(emptyNode)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.updateV2(emptyNode);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }

    /**
     * Test case for updateV2 method when a valid playlist update request is made.
     * It verifies that the method returns the expected ResponseEntity with the correct ApiResponse.
     */
    @Test
    public void test_updateV2_validPlaylistUpdate() throws JsonProcessingException {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode playListDetails = objectMapper.readTree("{\"id\":\"123\",\"name\":\"Updated Playlist\"}");

        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.updateV2PlayList(playListDetails)).thenReturn(mockResponse);

        // Act
        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.updateV2(playListDetails);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void testUpdateV1Playlist() throws JsonProcessingException {
        JsonNode playListDetails = new ObjectMapper().readTree("{\"id\":\"1\",\"name\":\"Playlist V1\"}");
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.updatePlayList(playListDetails)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.update(playListDetails);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
    }

    @Test
    void testPlayListReadV1() {
        String id = "playlist1";
        String orgId = "org1";
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.readPlaylist(id, orgId)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.playListRead(id, orgId);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
        verify(playListSerive).readPlaylist(id, orgId);
    }

    @Test
    void testPlayListSearchWithoutCaching() {
        SearchCriteria searchDto = new SearchCriteria();
        ApiResponse mockResponse = new ApiResponse();
        mockResponse.setResponseCode(HttpStatus.OK);

        when(playListSerive.searchPlayListWithoutCaching(searchDto)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse> result = (ResponseEntity<ApiResponse>) playListController.playListSearchWithoutCaching(searchDto);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(mockResponse, result.getBody());
        verify(playListSerive).searchPlayListWithoutCaching(searchDto);
    }

    @Test
    void testCreateV2_ExceptionHandling() {
        JsonNode playListDetails = new ObjectMapper().createObjectNode();
        when(playListSerive.createV2PlayList(any())).thenThrow(new RuntimeException("Service exception"));

        try {
            playListController.createV2(playListDetails);
        } catch (RuntimeException ex) {
            assertEquals("Service exception", ex.getMessage());
        }
    }

    @Test
    void testUpdateV2_ExceptionHandling() throws JsonProcessingException {
        JsonNode playListDetails = new ObjectMapper().createObjectNode();
        when(playListSerive.updateV2PlayList(any())).thenThrow(new RuntimeException("Service exception"));

        try {
            playListController.updateV2(playListDetails);
        } catch (RuntimeException ex) {
            assertEquals("Service exception", ex.getMessage());
        }
    }


}
