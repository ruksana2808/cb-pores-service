package com.igot.cb.playlist.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.playlist.dto.SearchDto;
import com.igot.cb.playlist.entity.PlayListEntity;
import com.igot.cb.playlist.repository.PlayListRepository;
import com.igot.cb.playlist.service.ContentService;
import com.igot.cb.playlist.util.RedisCacheMngr;
import com.igot.cb.pores.Service.OutboundRequestHandlerServiceImpl;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayListServiceImpl2Test {

    @InjectMocks
    private PlayListServiceImpl playListService;

    @Mock
    private PlayListRepository playListRepository;

    @Mock
    private RedisTemplate<String, PlayListEntity> playListEntityRedisTemplate;

    @Mock
    private RedisCacheMngr redisCacheMngr;

    @Mock
    private ContentService contentService;

    @Mock
    private CbServerProperties cbServerProperties;

    @Mock
    private PayloadValidation payloadValidation;

    @Mock
    private EsUtilService esUtilService;

    @Mock
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Mock
    private CacheService cacheService;

    @Mock
    private PlayListEntity playListEntity;

    @Mock
    private JsonNode childrenNode;

    @Mock
    private JsonNode enrichedChildrenNode;

    @Mock
    private OutboundRequestHandlerServiceImpl outboundRequestHandlerService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String REDIS_INDEX = "12";
    private static final String ORG_ID = "org123";
    private static final String ID = "someKey";
    private static final String PLAYLIST_ID = "playlist456";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        playListService = new PlayListServiceImpl();
        injectField("objectMapper", objectMapper);
        injectField("playListRepository", playListRepository);
        injectField("redisCacheMngr", redisCacheMngr);
        injectField("cbServerProperties", cbServerProperties);
        injectField("outboundRequestHandlerService", outboundRequestHandlerService);
        injectField("redisInsightIndex", 1);
        injectField("payloadValidation", payloadValidation);
    }

    private void injectField(String fieldName, Object value) {
        try {
            var field = PlayListServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(playListService, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    void testValidatePayload_valid() {
        Map<String, Object> filters = new HashMap<>();
        filters.put(Constants.ORGANISATION, "org1");
        filters.put(Constants.REQUEST_TYPE, List.of("playlist"));
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.FILTERS, filters);
        SearchDto dto = new SearchDto();
        dto.setRequest((HashMap<String, Object>) request);
        assertDoesNotThrow(() -> invokeValidatePayload(dto));
    }

    private void invokeValidatePayload(SearchDto dto) {
        try {
            var method = PlayListServiceImpl.class.getDeclaredMethod("validatePayload", SearchDto.class);
            method.setAccessible(true);
            method.invoke(playListService, dto);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testFetchContentDetails_withValidArray() throws Exception {
        ArrayNode arrayNode = objectMapper.createArrayNode().add("content-id-1");

        Map<String, Object> responseMock = Map.of("result", Map.of("content", List.of("data")));
        when(cbServerProperties.getCourseCategoryFacet()).thenReturn(new String[]{"category"});
        when(cbServerProperties.getSbSearchServiceHost()).thenReturn("http://host");
        when(cbServerProperties.getSbCompositeV4Search()).thenReturn("/search");
        when(outboundRequestHandlerService.fetchResultUsingPost(anyString(), any(), any())).thenReturn(responseMock);

        var method = PlayListServiceImpl.class.getDeclaredMethod("fetchContentDetails", JsonNode.class);
        method.setAccessible(true);
        Object result = method.invoke(playListService, arrayNode);

        assertNotNull(result);
        assertTrue(result instanceof Map);
        assertEquals(responseMock, result);
    }

    @Test
    void testPersistInRedis_success() throws Exception {
        ObjectNode enriched = objectMapper.createObjectNode();
        enriched.put("id", "org1");
        enriched.put("children", objectMapper.createArrayNode().add("c1"));

        PlayListEntity entity = new PlayListEntity();
        entity.setOrgId("org1");
        entity.setRequestType("playlist");

        var method = PlayListServiceImpl.class.getDeclaredMethod("persistInRedis", ObjectNode.class, PlayListEntity.class, String.class);
        method.setAccessible(true);
        doNothing().when(redisCacheMngr).hset(any(), anyInt(), any());

        method.invoke(playListService, enriched, entity, "org1playlist");
        verify(redisCacheMngr, times(1)).hset(any(), anyInt(), any());
    }

    @Test
    void test_updateV2PlayList_whenExceptionThrown_logsError() {
        // Arrange
        JsonNode mockPlayListDetails = mock(ObjectNode.class);
        when(mockPlayListDetails.has(Constants.ID)).thenReturn(true);
        when(mockPlayListDetails.get(Constants.ID)).thenReturn(new TextNode("playlist123"));

        PlayListEntity entity = new PlayListEntity();
        entity.setId("playlist123");
        entity.setIsActive(true);
        entity.setOrgId("org1");
        entity.setRequestType("type1");
        ObjectNode data = new ObjectMapper().createObjectNode();
        entity.setData(data);

        when(playListRepository.findByIdAndIsActive("playlist123", true)).thenReturn(entity);

        // Force exception on save
        when(playListRepository.save(any())).thenThrow(new RuntimeException("DB save failed"));

        // Act
        ApiResponse response = playListService.updateV2PlayList(mockPlayListDetails);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
    }

    @Test
    void test_createV2PlayList_whenExceptionThrown_logsError() {
        ObjectNode input = new ObjectMapper().createObjectNode();
        input.put(Constants.ORG_ID, "org123");
        input.put(Constants.RQST_CONTENT_TYPE, "course");
        input.put(Constants.TITLE, "Sample Playlist");

        doNothing().when(payloadValidation).validatePayload(anyString(), eq(input));
        when(playListRepository.save(any())).thenThrow(new RuntimeException("Simulated failure"));

        ApiResponse response = playListService.createV2PlayList(input);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("Not found", response.getParams().getErrMsg());
    }


}

