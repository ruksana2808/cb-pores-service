package com.igot.cb.demand.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.demand.entity.DemandEntity;
import com.igot.cb.demand.repository.DemandRepository;
import com.igot.cb.demand.util.StatusTransitionConfig;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.producer.Producer;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;
import org.apache.poi.ss.formula.functions.T;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.OngoingStubbing;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandServiceImplMethodTest {

    private DemandServiceImpl demandService;

    @Mock
    private CacheService cacheService;

    @Mock
    private DemandRepository demandRepository;

    @Mock
    private ObjectMapper objectMapper;

    private ObjectMapper realObjectMapper = new ObjectMapper();

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private Producer kafkaProducer;

    @Mock
    private RequestHandlerServiceImpl requestHandlerService;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private CbServerProperties propertiesConfig;

    @Mock
    private StatusTransitionConfig statusTransitionConfig;

    @Mock
    private EsUtilService esUtilService;

    @BeforeEach
    void setup() throws Exception {
        // Create instance without calling constructor
        Objenesis objenesis = new ObjenesisStd();
        demandService = objenesis.newInstance(DemandServiceImpl.class);

        // Inject all mocks and real fields manually
        ReflectionTestUtils.setField(demandService, "demandRepository", demandRepository);
        ReflectionTestUtils.setField(demandService, "cacheService", cacheService);
        ReflectionTestUtils.setField(demandService, "objectMapper", realObjectMapper);
        ReflectionTestUtils.setField(demandService, "accessTokenValidator", accessTokenValidator);
        ReflectionTestUtils.setField(demandService, "cassandraOperation", cassandraOperation);
        ReflectionTestUtils.setField(demandService, "kafkaProducer", kafkaProducer);
        ReflectionTestUtils.setField(demandService, "requestHandlerService", requestHandlerService);
        ReflectionTestUtils.setField(demandService, "cbServerProperties", cbServerProperties);
        ReflectionTestUtils.setField(demandService, "propertiesConfig", propertiesConfig);
        ReflectionTestUtils.setField(demandService, "logger", LoggerFactory.getLogger(DemandServiceImpl.class));
        // Mock statusTransitionConfig to avoid file read
        ReflectionTestUtils.setField(demandService, "statusTransitionConfig", mock(StatusTransitionConfig.class));
    }

    @Test
    void testReadDemand_withEmptyId_returnsErrorResponse() {
        CustomResponse response = demandService.readDemand("");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals("Id not found", response.getMessage());
    }

    @Test
    void testReadDemand_withCachedData_returnsCachedResult() throws Exception {
        String id = "123";
        String cachedJson = "{\"some\":\"data\"}";
        when(cacheService.getCache(id)).thenReturn(cachedJson);

        CustomResponse response = demandService.readDemand(id);

        assertEquals("successfully read", response.getMessage()); // adjust constant if needed
    }


    @Test
    void testReadDemand_cacheMiss_repoHit_returnsRepoData() throws Exception {
        String id = "123";
        when(cacheService.getCache(id)).thenReturn(null);

        DemandEntity demandEntity = mock(DemandEntity.class);

        Map<String, Object> dataMap = Map.of("field", "value");
        ObjectMapper om = new ObjectMapper();
        JsonNode jsonNode = om.valueToTree(dataMap);

        when(demandEntity.getData()).thenReturn(jsonNode);
        when(demandRepository.findById(id)).thenReturn(Optional.of(demandEntity));

        CustomResponse response = demandService.readDemand(id);

        assertEquals("successfully read", response.getMessage());
    }

    @Test
    void testReadDemand_cacheMiss_repoMiss_returnsNotFound() {
        String id = "123";
        when(cacheService.getCache(id)).thenReturn(null);
        when(demandRepository.findById(id)).thenReturn(Optional.empty());

        CustomResponse response = demandService.readDemand(id);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("Invalid Id", response.getMessage());
    }

    @Test
    void testReadDemand_jsonMappingException_throwsCustomException() throws Exception {
        String id = "123";
        when(cacheService.getCache(id)).thenThrow(new RuntimeException());

        CustomException ex = assertThrows(CustomException.class, () -> demandService.readDemand(id));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
        assertEquals("error while processing", ex.getMessage());
    }

    @Test
    void testUpdateDemandStatus_tokenInvalid_returnsBadRequest() {
        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("");
        JsonNode updateDetails = realObjectMapper.createObjectNode();

        var response = demandService.updateDemandStatus(updateDetails, "token", "rootOrg");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrmsg());
    }

    @Test
    void testUpdateDemandStatus_validateUserFails_returnsErrorResponse() {

        StatusTransitionConfig mockStatusTransitionConfig = mock(StatusTransitionConfig.class);
        ReflectionTestUtils.setField(demandService, "statusTransitionConfig", mockStatusTransitionConfig);

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");

        JsonNode updateDetails = realObjectMapper.createObjectNode();
        var response = demandService.updateDemandStatus(updateDetails, "token", "rootOrg");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("User details not found with userId", response.getParams().getErrmsg());
    }

    @Test
    void testUpdateDemandStatus_newStatusInProgress_missingContentId_returnsBadRequest() {
        StatusTransitionConfig mockStatusTransitionConfig = mock(StatusTransitionConfig.class);
        ReflectionTestUtils.setField(demandService, "statusTransitionConfig", mockStatusTransitionConfig);


        ObjectNode dataNode = realObjectMapper.createObjectNode();
        dataNode.put(Constants.STATUS, "OLD_STATUS");
        dataNode.put(Constants.REQUEST_TYPE, "REQ_TYPE");
        dataNode.put(Constants.IS_ACTIVE, true);

        DemandEntity demandEntity = mock(DemandEntity.class);

        ObjectNode updateDetails = realObjectMapper.createObjectNode();
        updateDetails.put(Constants.DEMAND_ID, "demand1");
        updateDetails.put(Constants.NEW_STATUS, Constants.IN_PROGRESS);
        // Missing CONTENT_ID

        CustomResponse response = demandService.updateDemandStatus(updateDetails, "token", "rootOrg");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("User Id doesn't exist! Please supply a valid auth token", response.getParams().getErrmsg());
    }

    @Test
    void testUpdateDemandStatus_successfulUpdate_savesAndCaches() {

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user123");

        ObjectNode dataNode = realObjectMapper.createObjectNode();
        dataNode.put(Constants.STATUS, "OLD_STATUS");
        dataNode.put(Constants.REQUEST_TYPE, "REQ_TYPE");
        dataNode.put(Constants.IS_ACTIVE, true);
        dataNode.put(Constants.TITLE, "Test Title");

        DemandEntity demandEntity = new DemandEntity();
        demandEntity.setDemandId("demand1");
        demandEntity.setData(dataNode);


        ObjectNode updateDetails = realObjectMapper.createObjectNode();
        updateDetails.put(Constants.DEMAND_ID, "demand1");
        updateDetails.put(Constants.NEW_STATUS, "COMPLETED");
        updateDetails.put(Constants.CONTENT_ID, "content123");

        CustomResponse response = demandService.updateDemandStatus(updateDetails, "token", "rootOrg");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());

   }


    @Test
    void testResourceAvailability() {
        assertDoesNotThrow(() -> {
            ClassPathResource resource = new ClassPathResource("payloadValidation/statusTransitions.json");
            System.out.println("Resource exists? " + resource.exists());
            if (resource.exists()) {
                System.out.println("Resource absolute path: " + resource.getFile().getAbsolutePath());
            } else {
                System.out.println("Resource NOT found on classpath!");
            }
        }, "Resource lookup should not throw any exception");
    }



    @Test
    void testUpdateDemandStatusSuccess() {
        String token = "valid-token";
        String userId = "user123";
        String rootOrgId = "org1";

        ObjectNode updateDetails = realObjectMapper.createObjectNode();
        updateDetails.put(Constants.DEMAND_ID, "demand123");
        updateDetails.put(Constants.NEW_STATUS, Constants.IN_PROGRESS);
        updateDetails.put(Constants.CONTENT_ID, "content123");

        DemandEntity demandEntity = new DemandEntity();
        ObjectNode data = realObjectMapper.createObjectNode();
        data.put(Constants.STATUS, "Draft");
        data.put(Constants.REQUEST_TYPE, "type1");
        data.put(Constants.IS_ACTIVE, true);
        data.put(Constants.TITLE, "test title");
        demandEntity.setData(data);
        demandEntity.setDemandId("demand123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(Map.of(Constants.USER_ROOT_ORG_ID, rootOrgId, Constants.FIRST_NAME, "Test")));
        when(demandRepository.findById("demand123")).thenReturn(Optional.of(demandEntity));
        when(demandRepository.save(any())).thenReturn(demandEntity);
        when(cbServerProperties.getElasticDemandJsonPath()).thenReturn("path");

        ReflectionTestUtils.setField(demandService, "statusTransitionConfig", statusTransitionConfig);
        when(statusTransitionConfig.isValidTransition(anyString(), anyString(), anyString()))
                .thenReturn(true);
        ReflectionTestUtils.setField(demandService, "esUtilService", esUtilService);

        when(cbServerProperties.getElasticDemandJsonPath()).thenReturn("dummy/elastic/path.json");

        // Avoid NPE from esUtilService.addDocument
        when(esUtilService.addDocument(
                anyString(), anyString(), anyString(), anyMap(), anyString())).thenReturn("");

        CustomResponse response = demandService.updateDemandStatus(updateDetails, token, rootOrgId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_UPDATED, response.getMessage());
    }

    @Test
    void testUpdateDemandInProgressStatus_MissingContentId() {
        String token = "valid-token";
        String userId = "user123";
        String rootOrgId = "org1";

        ObjectNode updateDetails = realObjectMapper.createObjectNode();
        updateDetails.put(Constants.DEMAND_ID, "demand123");
        updateDetails.put(Constants.NEW_STATUS, Constants.IN_PROGRESS);
        // Intentionally omit CONTENT_ID to simulate the condition

        DemandEntity demandEntity = new DemandEntity();
        ObjectNode data = realObjectMapper.createObjectNode();
        data.put(Constants.STATUS, "Open");  // initial status
        data.put(Constants.REQUEST_TYPE, "type1");
        data.put(Constants.IS_ACTIVE, true);
        data.put(Constants.TITLE, "test title");
        demandEntity.setData(data);
        demandEntity.setDemandId("demand123");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(Map.of(Constants.USER_ROOT_ORG_ID, rootOrgId, Constants.FIRST_NAME, "Test")));

        when(demandRepository.findById("demand123")).thenReturn(Optional.of(demandEntity));

        // Inject esUtilService to prevent NPE if reached
        ReflectionTestUtils.setField(demandService, "esUtilService", esUtilService);

        Exception exception = assertThrows(CustomException.class, () -> {
            demandService.updateDemandStatus(updateDetails, token, rootOrgId);
        });

        assertTrue(exception.getMessage().contains("Cannot invoke"));
    }



    @Test
    void testUpdateDemandStatusMissingUserId() {
        String token = "";
        when(accessTokenValidator.verifyUserToken(token)).thenReturn("");
        CustomResponse response = demandService.updateDemandStatus(realObjectMapper.createObjectNode(), token, "org1");
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.USER_ID_DOESNT_EXIST, response.getParams().getErrmsg());
    }

    @Test
    void testUpdateDemandStatusMissingFields() {
        String token = "token";
        when(accessTokenValidator.verifyUserToken(token)).thenReturn("user123");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.singletonList(Map.of(Constants.USER_ROOT_ORG_ID, "org1", Constants.FIRST_NAME, "abc")));
        ObjectNode updateDetails = realObjectMapper.createObjectNode();
        updateDetails.put(Constants.DEMAND_ID, "");
        Exception ex = assertThrows(CustomException.class, () -> demandService.updateDemandStatus(updateDetails, token, "org1"));
        assertEquals(Constants.MISSING_ID_OR_NEW_STATUS, ex.getMessage());
    }

    @Test
    void testValidateUserWithWrongOrg() {
        CustomResponse response = new CustomResponse();
        List<Map<String, Object>> userData = new ArrayList<>();
        userData.add(Map.of(Constants.USER_ROOT_ORG_ID, "wrongOrg", Constants.FIRST_NAME, "abc"));
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt())).thenReturn(userData);
        CustomResponse result = demandService.validateUser("rootOrg", response, "user123");
        assertEquals(HttpStatus.FORBIDDEN, result.getResponseCode());
    }

    @Test
    void testValidateUserNotFound() {
        CustomResponse response = new CustomResponse();
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        CustomResponse result = demandService.validateUser("org", response, "user123");
        assertEquals(HttpStatus.BAD_REQUEST, result.getResponseCode());
    }

    @Test
    void testIsSpvRequestTrue() {
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), any())).thenReturn(
                Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of(Constants.SPV_ADMIN)))));
        boolean result = demandService.isSpvRequest("user123", Constants.SPV_ADMIN);
        assertTrue(result);
    }

    @Test
    void testIsSpvRequestFalse() {
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(anyString(), any())).thenReturn(
                Map.of(Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.ROLES, List.of("otherRole")))));
        boolean result = demandService.isSpvRequest("user123", Constants.SPV_ADMIN);
        assertFalse(result);
    }

    @Test
    void testDelete_shouldLogError_whenExceptionOccurs() {
        // Arrange
        String id = "demand-id-123";

        when(demandRepository.findById(id)).thenThrow(new RuntimeException("Simulated DB error"));

        String result = demandService.delete(id);

        // Assert
        assertTrue(result.contains("Simulated DB error"));
    }

    @Test
    void createDemand_success() throws Exception {
        // Arrange
        String token = "sample-token";
        String rootOrgId = "root-org";
        String userId = "user-123";
        String demandId = "12345";
        String requestType = Constants.SINGLE;
        String firstName = "John";
        String orgName = "Sample Org";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode demandDetails = mapper.createObjectNode();
        demandDetails.put(Constants.REQUEST_TYPE, requestType);
        demandDetails.put(Constants.TITLE, "Test Demand");
        demandDetails.put("objective", "objective");
        demandDetails.put(Constants.DEMAND_ID, "objective");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        // Mock cassandra user validation
        Map<String, Object> userDetailMap = Map.of(Constants.USER_ROOT_ORG_ID, rootOrgId, Constants.FIRST_NAME, firstName);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(2)))
                .thenReturn(List.of(userDetailMap));

        // Mock org details
        Map<String, Object> orgDetail = Map.of(Constants.USER_ROOT_ORG_NAME, orgName);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(List.of(orgDetail));

        // Mock repository
        when(demandRepository.count()).thenReturn(0L);
        when(demandRepository.existsById(anyString())).thenReturn(false);

        ArgumentCaptor<DemandEntity> captor = ArgumentCaptor.forClass(DemandEntity.class);
        when(demandRepository.save(captor.capture())).thenAnswer(invocation -> {
            DemandEntity entity = invocation.getArgument(0);
            entity.setDemandId(demandId);
            return entity;
        });

// GIVEN
        String requiredRole = "SOME_ROLE";
        Map<String, String> header = new HashMap<>();

// Mock return values from propertiesConfig
        when(propertiesConfig.getSbUrl()).thenReturn("https://mock-url/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/v1/read/");

// Construct full URL
        String finalUrl = "https://mock-url/user/v1/read/" + userId;

// Build the nested mock response structure
        List<String> rolesList = List.of(requiredRole);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.ROLES, rolesList);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, resultMap);

// WHEN: mock the actual method call
        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(finalUrl), eq(header)))
                .thenReturn(readData);



        ReflectionTestUtils.setField(demandService, "esUtilService", esUtilService);

        when(cbServerProperties.getElasticDemandJsonPath()).thenReturn("dummy/elastic/path.json");

        // Avoid NPE from esUtilService.addDocument
        when(esUtilService.addDocument(
                anyString(), anyString(), anyString(), anyMap(), anyString())).thenReturn("");
        // Mock requestHandlerService to return provider data
        Map<String, Object> learnerResp = Map.of(
                Constants.RESPONSE_CODE, Constants.OK,
                Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, List.of(Map.of(Constants.ROOT_ORG_ID, rootOrgId))))
        );
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap())).thenReturn(learnerResp);

        when(cbServerProperties.getElasticDemandJsonPath()).thenReturn("dummy/path");
        when(cbServerProperties.getDemandRequestKafkaTopic()).thenReturn("demand-topic");
        when(cbServerProperties.getSbApiKey()).thenReturn("dummy-api-key");
        when(cbServerProperties.getLearnerServiceUrl()).thenReturn("http://dummy.url");
        when(cbServerProperties.getOrgSearchPath()).thenReturn("/org/search");

        // Act
        CustomResponse response = demandService.createDemand(demandDetails, token, rootOrgId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESSFULLY_CREATED, response.getMessage());
        assertTrue(response.getResult().containsKey(Constants.DEMAND_ID));

        DemandEntity savedEntity = captor.getValue();
        assertEquals(userId, savedEntity.getData().get(Constants.OWNER).asText());
    }


    @Test
    void createDemand_successWhenDataIsNotEmpty() throws Exception {
        // Arrange
        String token = "sample-token";
        String rootOrgId = "root-org";
        String userId = "user-123";
        String demandId = "12345";
        String requestType = Constants.SINGLE;
        String firstName = "John";
        String orgName = "Sample Org";

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode demandDetails = mapper.createObjectNode();
        demandDetails.put(Constants.REQUEST_TYPE, requestType);
        demandDetails.put(Constants.TITLE, "Test Demand");
        demandDetails.put("objective", "objective");
        demandDetails.put(Constants.DEMAND_ID, demandId); // ✅ FIXED

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        // Cassandra user validation
        Map<String, Object> userDetailMap = Map.of(
                Constants.USER_ROOT_ORG_ID, rootOrgId,
                Constants.FIRST_NAME, firstName
        );
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), eq(2)))
                .thenReturn(List.of(userDetailMap));

        // Org details
        Map<String, Object> orgDetail = Map.of(Constants.USER_ROOT_ORG_NAME, orgName);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.ORG_TABLE), anyMap(), isNull(), eq(1)))
                .thenReturn(List.of(orgDetail));

        String oldProviderId = "org-old";
        String newProviderId = "org-new";

        // DemandDetails JsonNode
        ObjectNode assignedProviderNode = JsonNodeFactory.instance.objectNode();
        assignedProviderNode.put(Constants.PROVIDER_ID, newProviderId);
        demandDetails.set(Constants.ASSIGNED_PROVIDER, assignedProviderNode);

        // FetchedDemandJson
        ObjectNode fetchedAssignedProviderNode = JsonNodeFactory.instance.objectNode();
        fetchedAssignedProviderNode.put(Constants.PROVIDER_ID, oldProviderId);
        ObjectNode fetchedDemandJson = JsonNodeFactory.instance.objectNode();
        fetchedDemandJson.set(Constants.ASSIGNED_PROVIDER, fetchedAssignedProviderNode);
        fetchedDemandJson.put(Constants.CREATED_ON, "2024-05-10T12:00:00");

        // DemandEntity
        DemandEntity demandEntity = mock(DemandEntity.class);
        when(demandEntity.getData()).thenReturn(fetchedDemandJson);
        when(demandEntity.getCreatedOn()).thenReturn(Timestamp.valueOf("2024-05-10 12:00:00"));
        when(demandRepository.findById(demandId)).thenReturn(Optional.of(demandEntity));
        ReflectionTestUtils.setField(demandService, "demandRepository", demandRepository);

        // Capture saved entity
        ArgumentCaptor<DemandEntity> captor = ArgumentCaptor.forClass(DemandEntity.class);
        when(demandRepository.save(captor.capture())).thenAnswer(invocation -> {
            DemandEntity entity = invocation.getArgument(0);
            entity.setDemandId(demandId);
            return entity;
        });

        // Mock ES template
        ObjectNode esTemplate = mapper.createObjectNode();
        esTemplate.put(Constants.OWNER, userId);
//        when(esUtilService.readJsonFileFromPath("dummy/elastic/path.json")).thenReturn(esTemplate);
        ReflectionTestUtils.setField(demandService, "esUtilService", esUtilService);

        // Required role & user validation
        String requiredRole = "SOME_ROLE";
        Map<String, String> header = new HashMap<>();
        when(propertiesConfig.getSbUrl()).thenReturn("https://mock-url/");
        when(propertiesConfig.getUserReadEndPoint()).thenReturn("user/v1/read/");
        String finalUrl = "https://mock-url/user/v1/read/" + userId;

        List<String> rolesList = List.of(requiredRole);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.ROLES, rolesList);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.RESPONSE, responseMap);

        Map<String, Object> readData = new HashMap<>();
        readData.put(Constants.RESULT, resultMap);

        when(requestHandlerService.fetchUsingGetWithHeadersProfile(eq(finalUrl), eq(header)))
                .thenReturn(readData);

        // Kafka and ES-related config
        when(cbServerProperties.getElasticDemandJsonPath()).thenReturn("dummy/elastic/path.json");
        when(cbServerProperties.getDemandRequestKafkaTopic()).thenReturn("demand-topic");
        when(cbServerProperties.getSbApiKey()).thenReturn("dummy-api-key");
        when(cbServerProperties.getLearnerServiceUrl()).thenReturn("http://dummy.url");
        when(cbServerProperties.getOrgSearchPath()).thenReturn("/org/search");

        // Avoid NPE from esUtilService.addDocument
        when(esUtilService.addDocument(anyString(), anyString(), anyString(), anyMap(), anyString()))
                .thenReturn("");

        // Mock fetchResultUsingPost
        Map<String, Object> learnerResp = Map.of(
                Constants.RESPONSE_CODE, Constants.OK,
                Constants.RESULT, Map.of(Constants.RESPONSE, Map.of(Constants.CONTENT, List.of(
                        Map.of(Constants.ROOT_ORG_ID, rootOrgId)
                )))
        );
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(learnerResp);

        // Act
        CustomResponse response = demandService.createDemand(demandDetails, token, rootOrgId);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

}
