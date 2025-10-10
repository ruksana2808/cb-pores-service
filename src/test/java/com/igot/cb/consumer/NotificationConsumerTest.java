package com.igot.cb.consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.transactional.cassandrautils.CassandraOperation;
import com.igot.cb.transactional.service.RequestHandlerServiceImpl;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationConsumerTest {

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private RequestHandlerServiceImpl requestHandlerService;

    @Mock
    private CbServerProperties cbServerProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConsumerRecord<String, String> record;

    @BeforeEach
    void setUp() throws Exception {
        notificationConsumer = new NotificationConsumer();

        // Inject mocks
        injectField(notificationConsumer, "requestHandlerService", requestHandlerService);
        injectField(notificationConsumer, "configuration", cbServerProperties);
        setField(notificationConsumer, "mapper", objectMapper);

        record = new ConsumerRecord<>("topic", 0, 0L, null, "");
    }

    @Test
    void testDemandContentConsumer_validPayload_shouldCallProcessNotification() throws Exception {
        Map<String, Object> demandRequest = new HashMap<>();
        demandRequest.put(Constants.DATA, Map.of(Constants.STATUS, Constants.UNASSIGNED, Constants.ROOT_ORG_ID, "org123", Constants.PREFERRED_PROVIDER, List.of()));
        demandRequest.put(Constants.IS_SPV_REQUEST, false);

        String json = objectMapper.writeValueAsString(demandRequest);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 0, 0L, "key", json);

        lenient().when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                        any(), any(), any(), isNull(), anyInt()))
                .thenReturn(List.of(Map.of(Constants.USER_ROOT_ORG_NAME, "OrgName")));

        try (MockedStatic<CompletableFuture> mock = mockStatic(CompletableFuture.class)) {
            mock.when(() -> CompletableFuture.runAsync(any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        Runnable runnable = invocation.getArgument(0);
                        runnable.run();
                        return CompletableFuture.completedFuture(null);
                    });

            // Assertion: ensure the method executes without throwing exceptions
            assertDoesNotThrow(() -> notificationConsumer.demandContentConsumer(record),
                    "demandContentConsumer should execute without errors for valid payload");
        }
    }


    @Test
    void testDemandContentConsumer_invalidPayload_shouldLogError() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 0, 0L, "key", "{invalidJson");
        // Assertion: verify method handles invalid JSON gracefully
        assertDoesNotThrow(() -> notificationConsumer.demandContentConsumer(record),
                "demandContentConsumer should handle invalid JSON without throwing an exception");
    }


    @Test
    void testExtractAndFormatCompetencies() throws Exception {
        List<Map<String, String>> competencies = List.of(
                Map.of(Constants.AREA, "Area1", Constants.THEME, "Theme1", Constants.SUB_THEME, "SubTheme1")
        );

        Method method = NotificationConsumer.class.getDeclaredMethod("extractAndFormatCompetencies", List.class, String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(notificationConsumer, competencies, Constants.AREA);
        assertEquals("Area1.", result);
    }

    @Test
    void testSendNotificationToProvidersAsync_success() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(Constants.EMAIL_ID_LIST, Arrays.asList("user@example.com"));
        input.put(Constants.MDO_NAME, "MDO Org");
        input.put(Constants.ORG, "Org");
        input.put(Constants.COMPETENCY_AREA, "Area");
        input.put(Constants.COMPETENCY_THEMES, "Theme");
        input.put(Constants.COMPETENCY_SUB_THEMES, "SubTheme");
        input.put(Constants.DESCRIPTION, "Description");
        input.put(Constants.ORG_NAME, "Org Name");
        input.put(Constants.BODY, "Body text");
        input.put(Constants.SUB, "Subject");
        input.put(Constants.CREATED_BY, "creator-id");

        when(cbServerProperties.getSupportEmail()).thenReturn("noreply@example.com");
        when(cbServerProperties.getDemandRequestTemplate()).thenReturn("template-id");
        when(cbServerProperties.getNotificationAsyncPath()).thenReturn("/notify/email");
        when(cbServerProperties.getNotifyServiceHost()).thenReturn("http://notification-host");

        Map<String, Object> responseMap = Map.of("status", "success");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), isNull())).thenReturn(responseMap);

        Method method = NotificationConsumer.class.getDeclaredMethod("sendNotificationToProvidersAsync", Map.class);
        method.setAccessible(true);
        method.invoke(notificationConsumer, input);

        verify(requestHandlerService, times(1)).fetchResultUsingPost(contains("http://notification-host"), anyMap(), isNull());
    }

    @Test
    void testSendNotification_exceptionCaught() throws Exception {
        when(cbServerProperties.getNotifyServiceHost()).thenReturn("http://notification-host");

        Method method = NotificationConsumer.class.getDeclaredMethod("sendNotification", Map.class, String.class);
        method.setAccessible(true);

        doThrow(new RuntimeException("post error")).when(requestHandlerService)
                .fetchResultUsingPost(anyString(), anyMap(), isNull());

        Map<String, Object> dummyRequest = Map.of("key", "value");

        method.invoke(notificationConsumer, dummyRequest, "/notify/path");

        verify(requestHandlerService, times(1)).fetchResultUsingPost(anyString(), anyMap(), isNull());
    }

    @Test
    void testFetchEmailFromUserId_success() {
        List<String> userIds = List.of("user-1");

        when(cbServerProperties.getSbUrl()).thenReturn("http://localhost/");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("search");

        Map<String, Object> personalDetails = Map.of(Constants.PRIMARY_EMAIL, "test@example.com");
        Map<String, Object> profileDetails = Map.of(Constants.PERSONAL_DETAILS, personalDetails);
        Map<String, Object> userContent = Map.of(Constants.PROFILE_DETAILS, profileDetails);
        List<Map<String, Object>> contentList = List.of(userContent);
        Map<String, Object> response = Map.of(Constants.CONTENT, contentList);
        Map<String, Object> result = Map.of(Constants.RESPONSE, response);
        Map<String, Object> fullResponse = Map.of(Constants.RESPONSE_CODE, Constants.OK, Constants.RESULT, result);

        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(fullResponse);

        List<String> resultEmails = notificationConsumer.fetchEmailFromUserId(userIds);
        assertEquals(1, resultEmails.size());
        assertEquals("test@example.com", resultEmails.get(0));
    }

    @Test
    void testFetchEmailFromUserId_failureResponse() {
        List<String> userIds = List.of("user-1");

        when(cbServerProperties.getSbUrl()).thenReturn("http://localhost/");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("search");

        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(null);

        List<String> resultEmails = notificationConsumer.fetchEmailFromUserId(userIds);
        assertEquals(0, resultEmails.size());
    }

    @Test
    void testGetCBPAdminDetails_success() throws Exception {
        Set<String> rootOrgIds = Set.of("org1", "org2");

        // Prepare mock response structure to simulate the happy path
        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put(Constants.PRIMARY_EMAIL, "admin1@example.com");

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PERSONAL_DETAILS, personalDetails);

        Map<String, Object> content = new HashMap<>();
        content.put(Constants.ROOT_ORG_ID, "org1");
        content.put(Constants.PROFILE_DETAILS, profileDetails);

        List<Map<String, Object>> contents = List.of(content);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, contents);

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);

        Map<String, Object> searchProfileApiResp = new HashMap<>();
        searchProfileApiResp.put(Constants.RESPONSE_CODE, "OK");
        searchProfileApiResp.put(Constants.RESULT, result);

        when(cbServerProperties.getSbUrl()).thenReturn("http://dummyurl.com/");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("searchUser");

        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(searchProfileApiResp);

        List<String> emails = notificationConsumer.getCBPAdminDetails(rootOrgIds);
        assertNotNull(emails);
        assertEquals(1, emails.size());
        assertEquals("admin1@example.com", emails.get(0));
    }

    @Test
    void testGetCBPAdminDetails_emptyEmails_throwsException() {
        Set<String> rootOrgIds = Set.of("org1");

        // Response with empty contents list -> will cause emails list to be empty and exception thrown
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.CONTENT, Collections.emptyList());

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.RESPONSE, response);

        Map<String, Object> searchProfileApiResp = new HashMap<>();
        searchProfileApiResp.put(Constants.RESPONSE_CODE, "OK");
        searchProfileApiResp.put(Constants.RESULT, result);
        when(cbServerProperties.getSbUrl()).thenReturn("http://dummyurl.com/");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("searchUser");

        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(searchProfileApiResp);

        Exception ex = assertThrows(Exception.class, () -> notificationConsumer.getCBPAdminDetails(rootOrgIds));
        assertTrue(ex.getMessage().contains("Failed to find CBP Admin"));
    }

    @Test
    void testGetCBPAdminDetails_nullResponse_throwsException() {
        Set<String> rootOrgIds = Set.of("org1");

        when(cbServerProperties.getSbUrl()).thenReturn("http://dummyurl.com/");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("searchUser");
        // Simulate null response from fetchResultUsingPost
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), anyMap()))
                .thenReturn(null);

        Exception ex = assertThrows(Exception.class, () -> notificationConsumer.getCBPAdminDetails(rootOrgIds));
        assertTrue(ex.getMessage().contains("Failed to find CBP Admin"));
    }

    @Test
    void testHandleSpvRequest_assignedStatus() throws Exception {
        Map<String, Object> assignedProvider = Map.of(Constants.PROVIDER_NAME, "ProviderX");
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.ASSIGNED_PROVIDER, assignedProvider);
        request.put(Constants.OWNER, "owner1");
        request.put(Constants.DEMAND_ID, "demand123");

        // Mock fetchEmailFromUserId to return dummy email
        NotificationConsumer spyConsumer = Mockito.spy(notificationConsumer);
        doReturn(Collections.singletonList("owner1email@domain.com")).when(spyConsumer).fetchEmailFromUserId(anyList());

        Map<String, Object> mailDetails = new HashMap<>();
        ReflectionTestUtils.invokeMethod(spyConsumer, "handleSpvRequest", Constants.ASSIGNED, request, "MDO", mailDetails);

        assertTrue(mailDetails.containsKey(Constants.EMAIL_ID_LIST));
        assertTrue(mailDetails.containsKey(Constants.SUB));
        assertTrue(mailDetails.containsKey(Constants.BODY));
        assertEquals(Constants.SPV_ORG_NAME, mailDetails.get(Constants.ORG));
        assertEquals(Constants.SPV_ORG_NAME, mailDetails.get(Constants.ORG_NAME));
    }

    @Test
    void testHandleSpvRequest_invalidStatus() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.OWNER, "owner1");
        request.put(Constants.DEMAND_ID, "demand123");

        NotificationConsumer spyConsumer = Mockito.spy(notificationConsumer);
        doReturn(Collections.singletonList("owner1email@domain.com")).when(spyConsumer).fetchEmailFromUserId(anyList());

        Map<String, Object> mailDetails = new HashMap<>();
        ReflectionTestUtils.invokeMethod(spyConsumer, "handleSpvRequest", Constants.INVALID, request, "MDO", mailDetails);

        assertTrue(mailDetails.containsKey(Constants.EMAIL_ID_LIST));
        assertTrue(mailDetails.containsKey(Constants.SUB));
        assertTrue(mailDetails.containsKey(Constants.BODY));
        assertEquals(Constants.SPV_ORG_NAME, mailDetails.get(Constants.ORG));
        assertEquals(Constants.SPV_ORG_NAME, mailDetails.get(Constants.ORG_NAME));
    }

    @Test
    void testSendNotification_success() throws Exception {
        // Spy to test private sendNotification method
        NotificationConsumer spyConsumer = Mockito.spy(notificationConsumer);
        // Mock ObjectMapper to prevent errors in logging
        ObjectMapper mapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(spyConsumer, "mapper", mapper);
        Map<String, Object> request = new HashMap<>();
        String urlPath = "/notifyAsync";
        when(cbServerProperties.getNotifyServiceHost()).thenReturn("http://notifyhost");
        when(requestHandlerService.fetchResultUsingPost(anyString(), anyMap(), any()))
                .thenReturn(Collections.singletonMap("status", "ok"));
        when(mapper.writeValueAsString(any())).thenReturn("{}");
        // Assertion: method should execute successfully without exceptions
        assertDoesNotThrow(() ->
                        ReflectionTestUtils.invokeMethod(spyConsumer, "sendNotification", request, urlPath),
                "sendNotification should execute successfully for valid input");
    }


    @Test
    void testDemandContentConsumer_success() throws Exception {
        // Given
        String json = """
    {
        "data": {
            "status": "Unassigned",
            "rootOrgId": "root-123",
            "demand_id": "d1",
            "preferredProvider": [{"providerId": "prov-123"}],
            "competencies": [{
                "area": "Area1",
                "theme": "Theme1",
                "subTheme": "SubTheme1"
            }],
            "objective": "Improve skills"
        },
        "isSpvRequest": false
    }
    """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("test-topic", 0, 0L, "key", json);

        ReflectionTestUtils.setField(notificationConsumer, "requestHandlerService", requestHandlerService);
        ReflectionTestUtils.setField(notificationConsumer, "cassandraOperation", cassandraOperation);

        Map<String, Object> template1 = new HashMap<>();
        template1.put(Constants.TEMPLATE, "<html>Demand ID: $demandId, MDO: $mdoName</html>");
        List<Map<String, Object>> templateList1 = List.of(template1);

        when(cassandraOperation.getRecordsByPropertiesByKey(
                eq(Constants.KEYSPACE_SUNBIRD),
                eq(Constants.TABLE_EMAIL_TEMPLATE),
                eq(Map.of(Constants.NAME, "templateId")),
                eq(Collections.singletonList(Constants.TEMPLATE)),
                isNull()))
                .thenReturn(templateList1);

        Map<String, Object> orgDetails = new HashMap<>();
        orgDetails.put(Constants.USER_ROOT_ORG_NAME, "TestMDO");
        List<Map<String, Object>> orgList = List.of(orgDetails);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq("sunbird"), eq("organisation"), eq(Map.of("id", "root-123")), isNull(), eq(1)))
                .thenReturn(orgList);

        Map<String, Object> personalDetails = new HashMap<>();
        personalDetails.put("primaryEmail", "test@example.com");

        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put("personalDetails", personalDetails);

        Map<String, Object> userContent = new HashMap<>();
        userContent.put("rootOrgId", "prov-123");
        userContent.put("profileDetails", profileDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("content", List.of(userContent));

        Map<String, Object> result = new HashMap<>();
        result.put("response", response);

        Map<String, Object> searchResponse = new HashMap<>();
        searchResponse.put("result", result);
        searchResponse.put("responseCode", "OK");

        lenient().when(requestHandlerService.fetchResultUsingPost(anyString(), any(), any()))
                .thenReturn(searchResponse);

        when(cbServerProperties.getSbUrl()).thenReturn("http://localhost:9000");
        when(cbServerProperties.getUserSearchEndPoint()).thenReturn("/user/search");
        when(cbServerProperties.getSupportEmail()).thenReturn("support@test.org");
        when(cbServerProperties.getDemandRequestTemplate()).thenReturn("templateId");
        when(cbServerProperties.getNotifyServiceHost()).thenReturn("http://notify.local");
        when(cbServerProperties.getNotificationAsyncPath()).thenReturn("/v1/notify");

        // When & Then
        assertDoesNotThrow(() -> notificationConsumer.demandContentConsumer(record),
                "Method should process a valid demand content record without throwing any exceptions");
    }




    private void injectField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
