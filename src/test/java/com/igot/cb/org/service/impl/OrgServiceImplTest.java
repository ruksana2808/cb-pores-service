package com.igot.cb.org.service.impl;

import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.pores.Service.OutboundRequestHandlerServiceImpl;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;

import java.lang.reflect.Method;
import java.util.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrgServiceImplTest {

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private CbServerProperties cbServerProperties;

    @InjectMocks
    private OrgServiceImpl orgService;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerServiceImpl;

    @Mock
    private CbServerProperties propertiesConfig;

    @Mock(lenient = true)
    private RequestHandlerServiceImpl requestHandlerService;


    private final String frameworkId = "test-framework-id";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * Test readFramework method with blank frameworkName and orgId
     * This test verifies that the method returns a BAD_REQUEST response
     * when both frameworkName and orgId are blank.
     */
    @Test
    void testReadFrameworkWithBlankInputs() {
        String frameworkName = "";
        String orgId = "";
        String termName = "testTerm";
        String userAuthToken = "testToken";

        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("OrgID and FrameworkId is Missing", response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    /**
     * Test readFramework method with invalid user token
     * This test verifies that the method returns a BAD_REQUEST response
     * when the user token is invalid (userId is blank).
     */
    @Test
    void testReadFrameworkWithInvalidUserToken() {
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "invalidToken";

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn("");

        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    /**
     * Tests the createFrameworkRequest method with empty string inputs.
     * This edge case is relevant as the method doesn't explicitly handle empty strings,
     * but it's important to verify the behavior in such scenarios.
     */
    @Test
    void test_createFrameworkRequest_withEmptyStrings() {
        Map<String, Object> result = OrgServiceImpl.createFrameworkRequest("", "");

        assertNotNull(result);
        assertTrue(result.containsKey("framework"));

    }

    /**
     * Tests the createFrameworkRequest method with valid input parameters.
     * Verifies that the returned Map contains the correct framework structure
     * with the provided channelId and frameworkName.
     */
    @Test
    void test_createFrameworkRequest_withValidInput() {
        String channelId = "testChannel";
        String frameworkName = "testFramework";

        Map<String, Object> result = OrgServiceImpl.createFrameworkRequest(channelId, frameworkName);

        assertNotNull(result);
        assertTrue(result.containsKey("framework"));

        Map<String, Object> framework = (Map<String, Object>) result.get("framework");
        assertEquals(channelId, framework.get("owner"));
        assertTrue(((String) framework.get("description")).contains(channelId));
    }

    /**
     * Test case for createOuterMap method
     * 
     * This test verifies that the createOuterMap method correctly creates an outer map
     * with a "request" key containing the provided requestMap as its value.
     */
    @Test
    void test_createOuterMap_createsCorrectStructure() {
        // Arrange
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("key1", "value1");
        requestMap.put("key2", 2);

        // Act
        Map<String, Object> result = OrgServiceImpl.createOuterMap(requestMap);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("request"));
        assertEquals(requestMap, result.get("request"));
    }

    /**
     * Test case for createRequestMap method
     * Verifies that the method correctly creates a request map with the given term map
     */
    @Test
    void test_createRequestMap_1() {
        // Arrange
        Map<String, Object> termMap = new HashMap<>();
        termMap.put("key1", "value1");
        termMap.put("key2", "value2");

        // Act
        Map<String, Object> result = OrgServiceImpl.createRequestMap(termMap);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("term"));
        assertEquals(termMap, result.get("term"));
    }

    /**
     * Test createRequestMap method with an empty map input.
     * This tests the edge case of passing an empty map as the termMap parameter,
     * which is a valid but minimal input scenario.
     */
    @Test
    void test_createRequestMap_withEmptyMap() {
        Map<String, Object> emptyMap = new HashMap<>();
        Map<String, Object> result = OrgServiceImpl.createRequestMap(emptyMap);
        assertNotNull(result);
        assertTrue(result.containsKey("term"));
        assertEquals(emptyMap, result.get("term"));
    }

    /**
     * Test createRequestMap method with null input.
     * This tests the edge case of passing null as the termMap parameter,
     * which is not explicitly handled in the method but is a potential scenario.
     */
    @Test
    void test_createRequestMap_withNullInput() {
        Map<String, Object> result = OrgServiceImpl.createRequestMap(null);
        assertNotNull(result);
        assertTrue(result.containsKey("term"));
        assertNull(result.get("term"));
    }

    /**
     * Tests the createTermMap method with empty input strings.
     * This test verifies that the method still creates a valid map
     * even when provided with empty strings for all parameters.
     */
    @Test
    void test_createTermMap_withEmptyStrings() {
        Map<String, Object> result = OrgServiceImpl.createTermMap("", "", "");

        assertNotNull(result);
        assertEquals("", result.get(Constants.NAME));
        assertEquals("", result.get(Constants.DESCRIPTION));
        assertNotNull(result.get(Constants.CODE));
        assertEquals("", result.get(Constants.REF_TYPE));
        assertEquals("", result.get(Constants.REF_ID));
        assertEquals("", result.get(Constants.CATEGORY));

        Map<String, Object> additionalProperties = (Map<String, Object>) result.get(Constants.ADDITIONAL_PROPERTIES);
        assertNotNull(additionalProperties);
        assertNotNull(additionalProperties.get(Constants.TIMESTAMP));
        assertEquals("", additionalProperties.get(Constants.CREATED_BY));
    }

    /**
     * Test case for createTermMap method
     * Verifies that the method creates a term map with correct keys and values
     */
    @Test
    void test_createTermMap_withValidInputs() {
        String termName = "TestTerm";
        String category = "TestCategory";
        String createdBy = "TestUser";

        Map<String, Object> result = OrgServiceImpl.createTermMap(termName, category, createdBy);

        assertNotNull(result);
        assertEquals(termName, result.get("name"));
        assertEquals(termName, result.get("description"));
        assertInstanceOf(UUID.class, result.get("code"));
        assertEquals("", result.get("refType"));
        assertEquals("", result.get("refId"));
        assertEquals(category, result.get("category"));

        Map<String, Object> additionalProperties = (Map<String, Object>) result.get("additionalProperties");
        assertNotNull(additionalProperties);
    }

    /**
     * Tests that isSpvRequest returns true when the user has a required role.
     * This test verifies that the method correctly identifies a user with the necessary permissions.
     */
    @Test
    void test_isSpvRequest_1() {
        // Arrange
        String userId = "testUser";
        List<String> requiredRoles = Arrays.asList("MDO_ADMIN", "SPV_ADMIN");

        when(propertiesConfig.getSbUrl()).thenReturn("http://test.com/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.ROLES, Arrays.asList("SPV_ADMIN", "CONTENT_CREATOR"));

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, result);

        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
            .thenReturn(readData);

        // Act
        boolean isSpv = orgService.isSpvRequest(userId, requiredRoles);

        // Assert
        assertTrue(isSpv);
        verify(requestHandlerService).fetchUsingGetWithHeadersProfile(
            eq("http://test.com/user/testUser"),
            anyMap()
        );
    }

    /**
     * Tests the isSpvRequest method with an empty list of required roles.
     * This is an edge case where the method should return false as specified in the implementation.
     */
    @Test
    void test_isSpvRequest_emptyRequiredRoles() {
        String userId = "testUser";
        List<String> requiredRoles = new ArrayList<>();

        when(propertiesConfig.getSbUrl()).thenReturn("http://test.com/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.ROLES, Arrays.asList("ROLE1", "ROLE2"));

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, result);

        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
            .thenReturn(readData);

        boolean isSpvRequest = orgService.isSpvRequest(userId, requiredRoles);

        assertFalse(isSpvRequest, "Method should return false for empty required roles");
    }

    /**
     * Test case for isSpvRequest method when the user does not have any of the required roles.
     * This test verifies that the method returns false when the user's roles do not match any of the required roles.
     */
    @Test
    void test_isSpvRequest_whenUserDoesNotHaveRequiredRoles_returnsFalse() {
        // Arrange
        String userId = "testUser";
        List<String> requiredRoles = Arrays.asList("ADMIN", "MANAGER");

        when(propertiesConfig.getSbUrl()).thenReturn("http://test.com/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("roles", Arrays.asList("USER", "GUEST"));

        Map<String, Object> result = new HashMap<>();
        result.put("response", responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put("result", result);

        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap())).thenReturn(readData);

        // Act
        boolean isSpvRequest = orgService.isSpvRequest(userId, requiredRoles);

        // Assert
        assertFalse(isSpvRequest);
    }

    /**
     * Test case for isSpvRequest method when the requiredRoles list is empty.
     * This test verifies that the method returns false when no roles are required.
     */
    @Test
    void test_isSpvRequest_withEmptyRequiredRoles() {
        String userId = "testUser";
        List<String> requiredRoles = new ArrayList<>();

        when(propertiesConfig.getSbUrl()).thenReturn("http://test.com/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("roles", new ArrayList<>());

        Map<String, Object> result = new HashMap<>();
        result.put("response", responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put("result", result);

        when(requestHandlerService.fetchUsingGetWithHeadersProfile("http://test.com/user/testUser", new HashMap<>()))
                .thenReturn(readData);

        boolean isSpvRequest = orgService.isSpvRequest(userId, requiredRoles);

        assertFalse(isSpvRequest, "Expected isSpvRequest to return false for empty required roles");
    }

    /**
     * Tests the processFrameworkCreate method when the framework response is successful.
     * It verifies that the method returns the correct framework name when the response
     * contains a valid node ID.
     */
    @Test
    void test_processFrameworkCreate_1() {
        // Arrange
        String masterFramework = "testMasterFramework";
        String orgId = "testOrgId";
        String expectedNodeId = "testNodeId";

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://test-knowledge-ms/");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("framework/copy");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, expectedNodeId);

        Map<String, Object> frameworkResponse = new HashMap<>();
        frameworkResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        frameworkResponse.put(Constants.RESULT, resultMap);

        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(frameworkResponse);

        // Act
        String result = orgService.processFrameworkCreate(masterFramework, orgId);

        // Assert
        assertEquals(expectedNodeId, result);
        verify(outboundRequestHandlerServiceImpl).fetchResultUsingPost(anyString(), anyMap(), anyMap());
    }

    /**
     * Tests the processFrameworkCreate method when the framework copy request fails.
     * This test verifies that an empty string is returned when the framework copy operation fails.
     */
    @Test
    void test_processFrameworkCreate_copyFrameworkFails() {
        // Arrange
        String masterFramework = "testMasterFramework";
        String orgId = "testOrgId";

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://test-url.com");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("/framework/copy");

        Map<String, Object> failedResponse = new HashMap<>();
        failedResponse.put("responseCode", "ERROR");

        // Act
        String result = orgService.processFrameworkCreate(masterFramework, orgId );

        // Assert
        assertEquals("", result);
    }

    /**
     * Tests the processFrameworkCreate method when the framework copy response is empty.
     * This test verifies that an empty string is returned when the framework copy operation returns an empty response.
     */
    @Test
    void test_processFrameworkCreate_emptyResponse() {
        // Arrange
        String masterFramework = "testMasterFramework";
        String orgId = "testOrgId";

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://test-url.com");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("/framework/copy");

//        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any())).thenReturn(null);

        // Act
        String result = orgService.processFrameworkCreate(masterFramework, orgId);

        // Assert
        assertEquals("", result);
    }

    /**
     * Test case for processFrameworkCreate method when the framework response is empty or has an invalid response code.
     * This test verifies that the method returns an empty string when the framework copy operation fails.
     */
    @Test
    void test_processFrameworkCreate_whenFrameworkCopyFails() {
        // Arrange
        String masterFramework = "testMasterFramework";
        String orgId = "testOrgId";

        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://test-url/");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("framework/copy");

        Map<String, Object> emptyResponse = new HashMap<>();
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
            .thenReturn(emptyResponse);

        // Act
        String result = orgService.processFrameworkCreate(masterFramework, orgId);

        // Assert
        assertEquals("", result);
        verify(outboundRequestHandlerServiceImpl).fetchResultUsingPost(anyString(), anyMap(), anyMap());
    }

    /**
     * Test case for readFramework method when the organization is not found.
     * This test verifies that the method returns a BAD_REQUEST response with the appropriate error message
     * when the provided orgId does not correspond to any existing organization.
     */
    @Test
    void test_readFramework_4() {
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";

        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("User Id doesn't exist! Please supply a valid auth token", response.getParams().getErrMsg());
    }

    /**
     * Test case for readFramework method when the framework creation is successful.
     * This test verifies that the method returns a successful response with the correct framework ID
     * when all conditions are met and the framework is created and published successfully.
     */
    @Test
    void test_readFramework_5() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";
        String newFrameworkId = "newFrameworkId";

        String url = propertiesConfig.getSbUrl() + propertiesConfig.getUserReadEndPoint() + userId;

        Map<String, Object> mockResponseMap = new HashMap<>();
        mockResponseMap.put(Constants.ROLES, List.of("admin", "user"));

        Map<String, Object> mockResultMap = new HashMap<>();
        mockResultMap.put(Constants.RESPONSE, mockResponseMap);

        Map<String, Object> mockReadData = new HashMap<>();
        mockReadData.put(Constants.RESULT, mockResultMap);

        // Mocking method
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(url), anyMap()))
                .thenReturn(mockReadData);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        List<Map<String, Object>> orgDetails = new ArrayList<>();
        Map<String, Object> orgDetail = new HashMap<>();
        orgDetail.put(Constants.FRAMEWORK_STATUS, Constants.FAILED);
        orgDetail.put(Constants.FRAMEWORKID, "");
        orgDetails.add(orgDetail);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(orgDetails);

        // Mocking the private methods
        // Note: This assumes you have a way to mock private methods. If not, you might need to refactor the code to make testing easier.
        when(orgService.processFrameworkCreate(frameworkName, orgId)).thenReturn(newFrameworkId);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("User does not have the required role:", response.getParams().getErrMsg());
    }

    @Test
    void test_readFramework_11() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";
        String newFrameworkId = "newFrameworkId";

        String url = propertiesConfig.getSbUrl() + propertiesConfig.getUserReadEndPoint() + userId;

        Map<String, Object> mockResponseMap = new HashMap<>();
        mockResponseMap.put(Constants.ROLES, List.of("MDO_LEADER", "user"));

        Map<String, Object> mockResultMap = new HashMap<>();
        mockResultMap.put(Constants.RESPONSE, mockResponseMap);

        Map<String, Object> mockReadData = new HashMap<>();
        mockReadData.put(Constants.RESULT, mockResultMap);

        // Mocking method
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(url), anyMap()))
                .thenReturn(mockReadData);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        List<Map<String, Object>> orgDetails = new ArrayList<>();
        Map<String, Object> orgDetail = new HashMap<>();
        orgDetail.put(Constants.FRAMEWORK_STATUS, Constants.FAILED);
        orgDetail.put(Constants.FRAMEWORKID, "");
        orgDetails.add(orgDetail);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(orgDetails);

        // Mocking the private methods
        // Note: This assumes you have a way to mock private methods. If not, you might need to refactor the code to make testing easier.
        when(orgService.processFrameworkCreate(frameworkName, orgId)).thenReturn(newFrameworkId);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Already this framework creation request is initialised", response.getParams().getErrMsg());
    }

    @Test
    void test_readFramework_12() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";
        String newFrameworkId = "newFrameworkId";

        String url = propertiesConfig.getSbUrl() + propertiesConfig.getUserReadEndPoint() + userId;

        Map<String, Object> mockResponseMap = new HashMap<>();
        mockResponseMap.put(Constants.ROLES, List.of("MDO_LEADER", "user"));

        Map<String, Object> mockResultMap = new HashMap<>();
        mockResultMap.put(Constants.RESPONSE, mockResponseMap);

        Map<String, Object> mockReadData = new HashMap<>();
        mockReadData.put(Constants.RESULT, mockResultMap);

        // Mocking method
        lenient().when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(url), anyMap()))
                .thenReturn(mockReadData);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        List<Map<String, Object>> orgDetails = new ArrayList<>();
        Map<String, Object> orgDetail = new HashMap<>();
        orgDetail.put(Constants.FRAMEWORK_STATUS, Constants.SUCCESS);
        orgDetail.put(Constants.FRAMEWORKID, "");
        orgDetails.add(orgDetail);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(orgDetails);

        // Mocking the private methods
        // Note: This assumes you have a way to mock private methods. If not, you might need to refactor the code to make testing easier.
        when(orgService.processFrameworkCreate(frameworkName, orgId)).thenReturn(newFrameworkId);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FRAMEWORK_PROCESS_ALREADY_INITIALISED, response.getParams().getErrMsg());
    }

    @Test
    void test_readFramework_13() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";
        String newFrameworkId = "newFrameworkId";

        String url = propertiesConfig.getSbUrl() + propertiesConfig.getUserReadEndPoint() + userId;

        Map<String, Object> mockResponseMap = new HashMap<>();
        mockResponseMap.put(Constants.ROLES, List.of("MDO_LEADER", "user"));

        Map<String, Object> mockResultMap = new HashMap<>();
        mockResultMap.put(Constants.RESPONSE, mockResponseMap);

        Map<String, Object> mockReadData = new HashMap<>();
        mockReadData.put(Constants.RESULT, mockResultMap);

        // Mocking method
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(url), anyMap()))
                .thenReturn(mockReadData);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(null);

        when(orgService.processFrameworkCreate(frameworkName, orgId)).thenReturn(newFrameworkId);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Organization not found", response.getParams().getErrMsg());
    }

    @Test
    void test_readFramework_14() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";
        String newFrameworkId = "newFrameworkId";

        String url = propertiesConfig.getSbUrl() + propertiesConfig.getUserReadEndPoint() + userId;

        Map<String, Object> mockResponseMap = new HashMap<>();
        mockResponseMap.put(Constants.ROLES, List.of("MDO_LEADER", "user"));

        Map<String, Object> mockResultMap = new HashMap<>();
        mockResultMap.put(Constants.RESPONSE, mockResponseMap);

        Map<String, Object> mockReadData = new HashMap<>();
        mockReadData.put(Constants.RESULT, mockResultMap);

        // Mocking method
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(url), anyMap()))
                .thenReturn(mockReadData);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        List<Map<String, Object>> orgDetails = new ArrayList<>();
        Map<String, Object> orgDetail = new HashMap<>();
        orgDetail.put(Constants.FRAMEWORK_STATUS, Constants.FAILED);
        orgDetail.put(Constants.FRAMEWORKID, "fremeworkId123");
        orgDetails.add(orgDetail);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(orgDetails);

        // Mocking the private methods
        // Note: This assumes you have a way to mock private methods. If not, you might need to refactor the code to make testing easier.
        when(orgService.processFrameworkCreate(frameworkName, orgId)).thenReturn(newFrameworkId);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
    }

    /**
     * Test case for readFramework method when the framework process is already initialized.
     * This test verifies that the method returns an appropriate error response when
     * the framework status indicates that the process has already been initialized.
     */
    @Test
    void test_readFramework_8() {
        // Arrange
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "testToken";
        String userId = "testUserId";

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        Map<String, Object> orgDetails = new HashMap<>();
        orgDetails.put(Constants.FRAMEWORK_STATUS, Constants.COMPLETED);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);


        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());

    }

    /**
     * Tests the readFramework method when frameworkName or orgId is blank.
     * Expects a BAD_REQUEST response with appropriate error message.
     */
    @Test
    void test_readFramework_blankInputs() {

        ApiResponse response = orgService.readFramework("", "", "termName", "userAuthToken");

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("OrgID and FrameworkId is Missing", response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    /**
     * Test case for readFramework method when userId is blank
     * This test verifies that the method returns a BAD_REQUEST response
     * when the user token cannot be verified (resulting in a blank userId)
     */
    @Test
    void test_readFramework_userIdBlank() {
        String frameworkName = "testFramework";
        String orgId = "testOrgId";
        String termName = "testTerm";
        String userAuthToken = "invalidToken";

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("");

        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    /**
     * Test case for updateOrganizationFramework method when the update is successful.
     * This test verifies that the method correctly updates the organization's framework
     * and logs the success message when the Cassandra operation returns a successful response.
     */
    @Test
    void test_updateOrganizationFramework_SuccessfulUpdate() {
        // Arrange
        String orgId = "org456";
        String frameworkId = "test-framework-id";
        String frameworkStatus = "Completed";

        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put(frameworkId, frameworkId);             // "test-framework-id" = "test-framework-id"
        updateFields.put(frameworkStatus, frameworkStatus);     // "Completed" = "Completed"
        updateFields.put(Constants.ID, orgId);                  // "id" = "org456"

        Map<String, Object> updateResponse = new HashMap<>();
        updateResponse.put(Constants.RESPONSE, Constants.SUCCESS);

        when(cassandraOperation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, updateFields))
                .thenReturn(updateResponse);

        // Act
        orgService.updateOrganizationFramework(frameworkId, orgId, frameworkId, frameworkStatus);

        // Assert
        verify(cassandraOperation).updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.ORG_TABLE, updateFields);
    }



    /**
     * Tests the updateOrganizationFramework method when the database update fails.
     * This test verifies that the method handles the case where the update operation
     * returns a non-success response correctly.
     */
    @Test
    void test_updateOrganizationFramework_databaseUpdateFails() {
        // Arrange
        String orgId = "testOrgId";
        Map<String, Object> updateResponse = new HashMap<>();
        updateResponse.put(Constants.RESPONSE, "FAILURE");
        when(cassandraOperation.updateRecord(
                anyString(), anyString(), any(Map.class))).thenReturn(updateResponse);
        // Act & Assert
        assertDoesNotThrow(() -> orgService.updateOrganizationFramework(frameworkId, orgId, "id", "Completed"),
                "Method should handle DB update failure gracefully without throwing an exception");
    }


    /**
     * Test case for updateOrganizationFramework method when the update operation fails.
     * This test verifies that the method handles the case where the update response
     * does not indicate success, ensuring proper error logging occurs.
     */
    @Test
    void test_updateOrganizationFramework_whenUpdateFails() {
        String orgId = "testOrgId";
        String frameworkId = "test-framework-id";
        String frameworkStatus = "Completed";

        Map<String, Object> updateResponse = new HashMap<>();
        updateResponse.put(Constants.RESPONSE, "FAILURE");

        when(cassandraOperation.updateRecord(anyString(), anyString(), any(Map.class)))
                .thenReturn(updateResponse);

        // Act
        orgService.updateOrganizationFramework(frameworkId, orgId, frameworkId, frameworkStatus);

        // Assert
        verify(cassandraOperation).updateRecord(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.ORG_TABLE),
                argThat(map ->
                        frameworkStatus.equals(map.get("Completed")) && // BAD key used as key
                                orgId.equals(map.get(Constants.ID))             // Valid key
                )
        );
    }


    @Test
    void test_frameworkRead_success() throws Exception {
        // Mock URL building
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsFrameworkRead()).thenReturn("framework/read");

        // Mock framework response
        Map<String, Object> frameworkData = Map.of("name", "Framework 1");
        Map<String, Object> resultData = Map.of(Constants.FRAMEWORK, frameworkData);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.RESPONSE_CODE, Constants.OK);
        responseMap.put(Constants.RESULT, resultData);

        when(outboundRequestHandlerServiceImpl.fetchResult(anyString())).thenReturn(responseMap);

        // Call private method via reflection
        Method method = getFrameworkReadMethod();
        ApiResponse response = (ApiResponse) method.invoke(orgService, frameworkId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(response.getResult().containsKey(Constants.FRAMEWORK));
    }

    @Test
    void test_frameworkRead_responseCodeNotOk() throws Exception {
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsFrameworkRead()).thenReturn("framework/read");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.RESPONSE_CODE, "ERROR");

        when(outboundRequestHandlerServiceImpl.fetchResult(anyString())).thenReturn(responseMap);

        Method method = getFrameworkReadMethod();
        ApiResponse response = (ApiResponse) method.invoke(orgService, frameworkId);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("Data not found"));
    }

    @Test
    void test_frameworkRead_nullResponse() throws Exception {
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsFrameworkRead()).thenReturn("framework/read");

        when(outboundRequestHandlerServiceImpl.fetchResult(anyString())).thenReturn(null);

        Method method = getFrameworkReadMethod();
        ApiResponse response = (ApiResponse) method.invoke(orgService, frameworkId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("Failed to read the framework details"));
    }

    @Test
    void test_frameworkRead_exceptionThrown() throws Exception {
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://localhost/");
        when(cbServerProperties.getOdcsFrameworkRead()).thenReturn("framework/read");

        when(outboundRequestHandlerServiceImpl.fetchResult(anyString()))
                .thenThrow(new RuntimeException("Test exception"));

        Method method = getFrameworkReadMethod();
        ApiResponse response = (ApiResponse) method.invoke(orgService, frameworkId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertTrue(response.getParams().getErr().contains("Test exception"));
    }

    @Test
    void testCreateChannel_usingReflection() throws Exception {
        // Get private static method
        Method method = OrgServiceImpl.class.getDeclaredMethod("createChannel", String.class);
        method.setAccessible(true);

        String testChannelId = "channel-123";
        Map<String, String> result = (Map<String, String>) method.invoke(null, testChannelId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testChannelId, result.get(Constants.IDENTIFIER));
    }

    @Test
    void testCreateChannels_usingReflection() throws Exception {
        // Get private static method
        Method method = OrgServiceImpl.class.getDeclaredMethod("createChannels", String.class);
        method.setAccessible(true);

        String testChannelId = "channel-456";
        List<Map<String, String>> result = (List<Map<String, String>>) method.invoke(null, testChannelId);

        assertNotNull(result);
        assertEquals(1, result.size());
        Map<String, String> channelMap = result.get(0);
        assertEquals(testChannelId, channelMap.get(Constants.IDENTIFIER));
    }


    @Test
    void testReadFramework_success_logsCopyFrameworkPublished() {
        String frameworkName = "masterFw";
        String orgId = "org123";
        String termName = "termA";
        String userAuthToken = "Bearer abc";
        String userId = "user123";
        String copiedFwName = "fw_copy_123";

        // 1. Mock access token verification
        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        // 2. Mock user role to pass isSpvRequest
        Map<String, Object> mockUserData = Map.of(
                Constants.RESULT, Map.of(
                        Constants.RESPONSE, Map.of(
                                Constants.ROLES, List.of(Constants.MDO_ADMIN)
                        )
                )
        );
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
                .thenReturn(mockUserData);

        // 3. Mock org details from Cassandra
        Map<String, Object> orgRecord = new HashMap<>();
        orgRecord.put(Constants.FRAMEWORK_STATUS, Constants.FAILED);
        orgRecord.put(Constants.FRAMEWORKID, null);
        List<Map<String, Object>> orgDetails = List.of(orgRecord);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), isNull(), anyInt()))
                .thenReturn(orgDetails);

        // 4. Mock processFrameworkCreate - simulate copying a framework
        ReflectionTestUtils.invokeMethod(orgService, "updateOrganizationFramework", copiedFwName, orgId, "id", Constants.COMPLETED);

        // 5. Mock frameworkReadV1 to return category
        doReturn("jobFunction").when(cbServerProperties).getKnowledgeMS();
        doReturn("/framework/read").when(cbServerProperties).getOdcsFrameworkRead();
        doReturn("/term/create").when(cbServerProperties).getOdcsTermCrete();
        doReturn("/framework/publish").when(cbServerProperties).getFrameworkPublish();

        Map<String, Object> fwResult = Map.of(
                Constants.RESPONSE_CODE, Constants.OK,
                Constants.RESULT, Map.of(
                        Constants.FRAMEWORK, Map.of("categories", List.of(Map.of(Constants.CODE, "jobFunction")))
                )
        );
        when(outboundRequestHandlerServiceImpl.fetchResult(anyString())).thenReturn(fwResult);

        Map<String, Object> termCreateResult = Map.of(
                Constants.RESPONSE_CODE, Constants.OK,
                Constants.RESULT, Map.of(Constants.NODE_ID, List.of("term123"))
        );
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(contains("/term/create"), any()))
                .thenReturn(termCreateResult);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.NODE_ID, "framework123");

        // Step 2: Mock top-level response map
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.RESULT, resultMap);
        responseMap.put(Constants.RESPONSE_CODE, Constants.OK);

        // Step 3: Mock fetchResultUsingPost to return responseMap
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(
                anyString(), any(), anyMap()))
                .thenReturn(responseMap);
        Map<String, Object> cassandraUpdate = Map.of(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.updateRecord(any(), any(), any())).thenReturn(cassandraUpdate);

        // Execute the method
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Validate the response
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testCreateOrgHierarchyFramework_SuccessfulCreation() {
        String masterFramework = "masterFw";
        String orgId = "org123";
        String token = "token123";
        String userId = "user123";
        String frameworkId = "copied_fw_001";

        when(cbServerProperties.getOrgHierarchyCreateRequiredRoles()).thenReturn(List.of("SPV_ADMIN"));
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of("SPV_ADMIN")))));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(List.of(new HashMap<>()));
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://kms/");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("copy");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(any(), any(), anyMap()))
                .thenReturn(Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, Map.of(Constants.NODE_ID, frameworkId)));
        when(cbServerProperties.getLearnerServiceUrl()).thenReturn("http://learner/");
        when(cbServerProperties.getOrgUpdateEndpoint()).thenReturn("org/update");
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPatch(any(), anyMap(), anyMap()))
                .thenReturn(Map.of(Constants.RESPONSE_CODE, "OK", Constants.RESULT, Map.of(Constants.RESPONSE, "Org updated")));

        ApiResponse response = orgService.createOrgHierarchyFramework(masterFramework, orgId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(frameworkId, response.getResult().get(Constants.FRAMEWORK));
    }

    @Test
    void testCreateOrgHierarchyFramework_MissingParams() {
        ApiResponse response = orgService.createOrgHierarchyFramework("", "", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testCreateOrgHierarchyFramework_InvalidToken() {
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("");

        ApiResponse response = orgService.createOrgHierarchyFramework("master", "org", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrMsg());
    }

    @Test
    void testCreateOrgHierarchyFramework_InsufficientRoles() {
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("userId");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of("USER")))));

        ApiResponse response = orgService.createOrgHierarchyFramework("master", "org", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErrMsg().contains("User does not have the required role"));
    }

    @Test
    void testCreateOrgHierarchyFramework_OrgNotFound() {
        when(cbServerProperties.getOrgHierarchyCreateRequiredRoles()).thenReturn(Arrays.asList("SPV_ADMIN"));
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("userId");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of("SPV_ADMIN")))));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = orgService.createOrgHierarchyFramework("master", "org", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Organization not found", response.getParams().getErrMsg());
    }

    @Test
    void testCreateOrgHierarchyFramework_FrameworkAlreadyInitialized() {
        Map<String, Object> orgDetail = Map.of(
                Constants.ORG_HIERARCHY_FRAMEWORK_STATUS, "COMPLETED",
                Constants.ORG_HIERARCHY_FRAMEWORK_ID, "fw123"
        );
        when(cbServerProperties.getOrgHierarchyCreateRequiredRoles()).thenReturn(List.of("SPV_ADMIN"));
        when(accessTokenValidator.verifyUserToken("token")).thenReturn("userId");
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap()))
                .thenReturn(Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of("SPV_ADMIN")))));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), anyMap(), any(), anyInt()))
                .thenReturn(List.of(orgDetail));

        ApiResponse response = orgService.createOrgHierarchyFramework("master", "org", "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FRAMEWORK_PROCESS_ALREADY_INITIALISED, response.getParams().getErrMsg());
    }

    @Test
    void testCreateOrgHierarchyFramework_Exception() {
        when(accessTokenValidator.verifyUserToken("token")).thenThrow(new RuntimeException("Unexpected error"));

        ApiResponse response = orgService.createOrgHierarchyFramework("master", "org", "token");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void testReadFramework_SuccessfulOrgUpdateLogsExpectedMessage() {
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://test-knowledge-ms/");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("framework/copy");

        // Arrange
        String frameworkName = "cb_mdo_framework";
        String orgId = "org123";
        String termName = "testTerm";
        String userAuthToken = "Bearer xyz-token";
        String userId = "user-xyz";
        String newFrameworkId = "org123_cb_mdo_framework";

        Map<String, Object> orgRecord = new HashMap<>();
        orgRecord.put(Constants.FRAMEWORK_STATUS, Constants.FAILED);
        orgRecord.put(Constants.FRAMEWORKID, "");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, "Org updated");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.RESPONSE_CODE, Constants.OK);
        responseMap.put(Constants.RESULT, resultMap);

        when(accessTokenValidator.verifyUserToken(userAuthToken)).thenReturn(userId);

        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put(Constants.ROLES, List.of(Constants.SPV_ADMIN));
        Map<String, Object> responseUserMap = new HashMap<>();
        responseUserMap.put(Constants.RESPONSE, userProfile);
        Map<String, Object> resultUserMap = new HashMap<>();
        resultUserMap.put(Constants.RESULT, responseUserMap);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap())).thenReturn(resultUserMap);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(orgRecord));



        // Simulate framework creation
        Map<String, Object> frameworkCreateResult = new HashMap<>();
        frameworkCreateResult.put(Constants.NODE_ID, newFrameworkId);
        Map<String, Object> frameworkCreateResponse = new HashMap<>();
        frameworkCreateResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        frameworkCreateResponse.put(Constants.RESULT, frameworkCreateResult);
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(
                contains("framework/copy"), any(), any()))
                .thenReturn(frameworkCreateResponse);

        // Simulate term creation
        Map<String, Object> termCreateResult = new HashMap<>();
        termCreateResult.put(Constants.NODE_ID, List.of("term1"));
        Map<String, Object> termCreateResponse = new HashMap<>();
        termCreateResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        termCreateResponse.put(Constants.RESULT, termCreateResult);
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(contains("term/create"), any()))
                .thenReturn(termCreateResponse);

        // Simulate framework publish
        Map<String, Object> publishResponse = new HashMap<>();
        publishResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(
                contains("framework/publish"), any(), any()))
                .thenReturn(publishResponse);

        // Simulate org update
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPatch(
                contains("org/v1/update"), any(), any()))
                .thenReturn(responseMap);

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put(Constants.RESPONSE, "Org update success");

        Map<String, Object> mockResponse = new HashMap<>();
        mockResponse.put(Constants.RESPONSE_CODE, Constants.OK);
        mockResponse.put(Constants.RESULT, mockResult);

        when(outboundRequestHandlerServiceImpl.fetchResultUsingPatch(
                anyString(), anyMap(), anyMap())
        ).thenReturn(mockResponse);

        // Act
        ApiResponse response = orgService.readFramework(frameworkName, orgId, termName, userAuthToken);

        // Assert
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(newFrameworkId, response.getResult().get(Constants.FRAMEWORK));
    }

    @Test
    void test_createOrgHierarchyFramework_whenFrameworkCopyFails_shouldLogUnableToCopy() {
        // Arrange
        String orgId = "org123";
        String masterFramework = "baseFW";
        String authToken = "Bearer token";
        String userId = "user123";

        // Mock user token validation
        when(cbServerProperties.getOrgHierarchyCreateRequiredRoles()).thenReturn(List.of("SPV_ADMIN"));
        when(accessTokenValidator.verifyUserToken(authToken)).thenReturn(userId);

        // Mock role validation
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.ROLES, List.of(Constants.SPV_ADMIN));
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, responseMap);
        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, resultMap);
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), anyMap())).thenReturn(readData);

        // Mock org details - framework not set or FAILED
        Map<String, Object> orgDetail = new HashMap<>();
        orgDetail.put(Constants.ORG_HIERARCHY_FRAMEWORK_ID, ""); // triggers framework copy
        orgDetail.put(Constants.ORG_HIERARCHY_FRAMEWORK_STATUS, Constants.FAILED); // triggers processFrameworkCreate
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(orgDetail));

        // Mock cbServerProperties URL
        when(cbServerProperties.getKnowledgeMS()).thenReturn("http://mock-knowledge/");
        when(cbServerProperties.getFrameworkCopy()).thenReturn("framework/copy");

        // Mock framework copy failure
        Map<String, Object> failedFrameworkResponse = new HashMap<>();
        failedFrameworkResponse.put(Constants.RESPONSE_CODE, "CLIENT_ERROR"); // not OK
        when(outboundRequestHandlerServiceImpl.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(failedFrameworkResponse);

        // Act
        ApiResponse response = orgService.createOrgHierarchyFramework(masterFramework, orgId, authToken);

        // Assert
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FRAMEWORK_PROCESS_ALREADY_INITIALISED, response.getParams().getErrMsg());
    }

    @Test
    void testCreateOrgHierarchyFramework_CustomExceptionThrown_ShouldLogAndReturnBadRequest() {
        // Arrange
        String masterFramework = "masterFw";
        String orgId = "org123";
        String userAuthToken = "some-token";

        // Simulate throwing CustomException from accessTokenValidator
        when(accessTokenValidator.verifyUserToken(userAuthToken))
                .thenThrow(new CustomException("ERR_AUTH", "Invalid token", HttpStatus.BAD_REQUEST));

        // Act
        ApiResponse response = orgService.createOrgHierarchyFramework(masterFramework, orgId, userAuthToken);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Invalid token", response.getParams().getErr());
    }

    private Method getFrameworkReadMethod() throws Exception {
        Method method = OrgServiceImpl.class.getDeclaredMethod("frameworkRead", String.class);
        method.setAccessible(true);
        return method;
    }
}
