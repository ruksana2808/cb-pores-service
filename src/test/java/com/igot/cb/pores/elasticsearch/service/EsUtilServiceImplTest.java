package com.igot.cb.pores.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class EsUtilServiceImplTest {


    private final ElasticsearchClient elasticsearchClient= Mockito.mock(ElasticsearchClient.class);
    private final ElasticsearchIndicesClient indicesClient = Mockito.mock(ElasticsearchIndicesClient.class);
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CbServerProperties cbServerProperties;

    @InjectMocks
    private EsUtilServiceImpl esUtilService;

    private SearchCriteria sampleCriteria;

    private static final String INDEX_NAME = "test-index";

    @Mock
    private Query query;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sampleCriteria = new SearchCriteria();
        sampleCriteria.setPageNumber(0);
        sampleCriteria.setPageSize(2);
        sampleCriteria.setSearchString("example");
        sampleCriteria.setRequestedFields(List.of("title", "description"));
        sampleCriteria.setFacets(List.of("category"));
        sampleCriteria.setOrderBy("title");
        sampleCriteria.setOrderDirection("asc");

        Map<String, Object> filters = new HashMap<>();
        filters.put("status", List.of("active", "pending"));
        filters.put("rating", Map.of("gte", JsonData.of(4)));
        sampleCriteria.setFilterCriteriaMap((HashMap<String, Object>) filters);
    }

    @Test
    void saveAll_shouldReturnBulkResponse_whenSuccess() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        String testId = "123";
        Map<String, Object> entityMap = Map.of("id", testId, "name", "Test");

        JsonNode jsonNode = mock(JsonNode.class);
        JsonNode idNode = mock(JsonNode.class);

        List<JsonNode> entities = List.of(jsonNode);

        when(jsonNode.get("id")).thenReturn(idNode);
        when(idNode.asText()).thenReturn(testId);
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(entityMap);

        BulkResponse mockResponse = mock(BulkResponse.class);
        lenient().when(elasticsearchClient.bulk((BulkRequest) any())).thenReturn(mockResponse);
        // Act
        BulkResponse response = esUtilService.saveAll(esIndexName, entities);

        // Assert
        assertNotNull(response);
    }

    @Test
    void testDeleteDocumentsByCriteria_noHits() throws IOException {
        // Given
        Query query = Query.of(q -> q.matchAll(m -> m));

        TotalHits totalHits = new TotalHits.Builder()
                .value(0L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> searchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(searchResponse);

        // When
        esUtilService.deleteDocumentsByCriteria("test-index", query);

        // Then
        verify(elasticsearchClient, never()).bulk(any(BulkRequest.class));
    }


    @Test
    void testDeleteDocumentsByCriteria_exceptionThrown() throws IOException {
        // Arrange
        Query query = Query.of(q -> q.matchAll(m -> m));
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenThrow(new IOException("search failed"));

        // Act
        esUtilService.deleteDocumentsByCriteria("test-index", query);

        // Assert
        verify(elasticsearchClient, never()).bulk(any(BulkRequest.class));
    }

    @Test
    void testAddDocument_schemaFileNotFound() {
        String result = esUtilService.addDocument("index", "1", "", Map.of("key", "val"), "/nonexistent.json");
        assertNull(result);
    }

    @Test
    void searchDocuments_Success() throws IOException {
        // Test data setup
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        String jsonFilePath = "/test-schema.json";

        // Mock SearchResponse
        SearchResponse<Object> mockSearchResponse = mock(SearchResponse.class);
        HitsMetadata<Object> hitsMetadata = mock(HitsMetadata.class);
        TotalHits totalHits = mock(TotalHits.class);
        List<Hit<Object>> hits = new ArrayList<>();

        // Configure mock responses
        when(totalHits.value()).thenReturn(1L);
        when(hitsMetadata.total()).thenReturn(totalHits);
        when(hitsMetadata.hits()).thenReturn(hits);
        when(mockSearchResponse.hits()).thenReturn(hitsMetadata);

        // Mock elasticsearch client search method
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);

        // Execute
        SearchResult result = esUtilService.searchDocuments(esIndexName, searchCriteria);

        // Verify
        assertNotNull(result);
        assertEquals(1L, result.getTotalCount());
    }


    @Test
    void deleteDocument_VerifyRefreshRequestParameters() throws IOException {
        String documentId = "test-doc-id";
        String esIndexName = "test-index";
        DeleteResponse mockDeleteResponse = mock(DeleteResponse.class);
        Result mockResult = mock(Result.class);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(mockResult.jsonValue()).thenReturn("DELETED");
        when(mockDeleteResponse.result()).thenReturn(mockResult);
        when(elasticsearchClient.delete(any(DeleteRequest.class))).thenReturn(mockDeleteResponse);
        ArgumentCaptor<RefreshRequest> refreshRequestCaptor = ArgumentCaptor.forClass(RefreshRequest.class);
        when(indicesClient.refresh(refreshRequestCaptor.capture())).thenReturn(null);
        esUtilService.deleteDocument(documentId, esIndexName);
        RefreshRequest capturedRequest = refreshRequestCaptor.getValue();
        assertEquals(esIndexName, capturedRequest.index().get(0));
    }


    @Test
    void test_successfulDeletion() throws IOException {
        Hit<Object> hit1 = new Hit.Builder<>().id("doc1").index("index").build();
        Hit<Object> hit2 = new Hit.Builder<>().id("doc2").index("index").build();
        HitsMetadata<Object> hits = new HitsMetadata.Builder<>()
                .total(new TotalHits.Builder().value(2L).relation(TotalHitsRelation.Eq).build())
                .hits(Arrays.asList(hit1, hit2))
                .build();

        ShardStatistics shards = new ShardStatistics.Builder()
                .total(1)
                .successful(1)
                .skipped(0)
                .failed(0)
                .build();

        SearchResponse<Object> mockResponse = new SearchResponse.Builder<Object>()
                .took(1L)
                .timedOut(false)
                .shards(shards)
                .hits(hits)  // your HitsMetadata<Object> here
                .build();

// Create a BulkResponseItem with a valid OperationType, e.g., DELETE
        BulkResponseItem item = new BulkResponseItem.Builder()
                .index("test-index")
                .id("doc1")
                .status(200)
                .operationType(OperationType.Delete)  // <-- correct enum value here
                .build();

        BulkResponse bulkResponse = new BulkResponse.Builder()
                .took(1L)
                .errors(false)
                .items(Collections.singletonList(item))
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        esUtilService.deleteDocumentsByCriteria(INDEX_NAME, query);

        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void test_partialDeletionFailure() throws IOException {
        Hit<Object> hit1 = new Hit.Builder<>().id("doc1").index("index").build();
        HitsMetadata<Object> hits = new HitsMetadata.Builder<>()
                .total(new TotalHits.Builder().value(2L).relation(TotalHitsRelation.Eq).build())
                .hits(Arrays.asList(hit1))
                .build();


        ShardStatistics shards = new ShardStatistics.Builder()
                .total(1)
                .successful(1)
                .skipped(0)
                .failed(0)
                .build();

        SearchResponse<Object> mockResponse = new SearchResponse.Builder<Object>()
                .took(1L)
                .timedOut(false)
                .shards(shards)
                .hits(hits)  // your HitsMetadata<Object> here
                .build();

// Create a BulkResponseItem with a valid OperationType, e.g., DELETE
        BulkResponseItem item = new BulkResponseItem.Builder()
                .index("test-index")
                .id("doc1")
                .status(200)
                .operationType(OperationType.Delete)  // <-- correct enum value here
                .build();

        BulkResponse bulkResponse = new BulkResponse.Builder()
                .took(1L)
                .errors(false)
                .items(Collections.singletonList(item))
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class))).thenReturn(mockResponse);
        when(elasticsearchClient.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        esUtilService.deleteDocumentsByCriteria(INDEX_NAME, query);

        verify(elasticsearchClient).bulk(any(BulkRequest.class));
    }

    @Test
    void test_exceptionHandling() throws IOException {
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("Simulated failure"));

        esUtilService.deleteDocumentsByCriteria(INDEX_NAME, query);

        verify(elasticsearchClient, never()).bulk((BulkRequest) any());
    }


    @Test
    void saveAll_shouldThrowCustomException_onBulkUploadFailure() throws IOException {
        // Arrange
        String indexName = "test-index";

        ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
        jsonNode.put("id", "123");
        jsonNode.put("name", "Test Entity");

        List<JsonNode> entities = Collections.singletonList(jsonNode);

        Map<String, Object> mockMap = new HashMap<>();
        mockMap.put("id", "123");
        mockMap.put("name", "Test Entity");

        when(objectMapper.convertValue(jsonNode, Map.class)).thenReturn(mockMap);
        when(elasticsearchClient.bulk(any(BulkRequest.class)))
                .thenThrow(new IOException("Bulk failure"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            esUtilService.saveAll(indexName, entities);
        });

        assertEquals("Bulk failure", exception.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getHttpStatusCode());

    }

    @Test
    void searchDocuments_shouldReturnNull_onIOException() throws IOException {
        // Arrange
        String indexName = "test-index";
        String jsonFilePath = "dummy.json"; // keep dummy, assuming method can handle it
        SearchCriteria criteria = new SearchCriteria();
        criteria.setPageNumber(0);
        criteria.setPageSize(10);

        // Force the Elasticsearch client to throw an IOException
        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenThrow(new IOException("ES failed"));

        // Act
        SearchResult result = esUtilService.searchDocuments(indexName, criteria);

        // Assert
        assertNull(result, "Expected result to be null due to IOException");
    }


    @Test
    void searchDocumentsV2_shouldHandleSearchString_withBoostConfiguration() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("spring boot tutorial");

        when(cbServerProperties.getSearchFieldsWithBoost())
                .thenReturn("title:2.0,summary:1.5,tags:1.0");

        TotalHits totalHits = new TotalHits.Builder()
                .value(25L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(25L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleEmptySearchString() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("");

        TotalHits totalHits = new TotalHits.Builder()
                .value(100L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleNullSearchString() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString(null);

        TotalHits totalHits = new TotalHits.Builder()
                .value(100L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleEmptyBoostConfiguration() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("test search");

        when(cbServerProperties.getSearchFieldsWithBoost()).thenReturn("");

        TotalHits totalHits = new TotalHits.Builder()
                .value(0L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleNullBoostConfiguration() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("test search");

        when(cbServerProperties.getSearchFieldsWithBoost()).thenReturn(null);

        TotalHits totalHits = new TotalHits.Builder()
                .value(0L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(0L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleEmptyFacets() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setFacets(Collections.emptyList());

        TotalHits totalHits = new TotalHits.Builder()
                .value(30L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(30L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleLargePaginationOffset() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(100);
        searchCriteria.setPageSize(50);

        TotalHits totalHits = new TotalHits.Builder()
                .value(10000L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(10000L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleEmptyStartsWith() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setStartsWith("");
        searchCriteria.setStartsWithField("title");

        TotalHits totalHits = new TotalHits.Builder()
                .value(100L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleNullStartsWithField() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setStartsWith("test");
        searchCriteria.setStartsWithField(null);

        TotalHits totalHits = new TotalHits.Builder()
                .value(100L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(100L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleSearchStringWithSpecialCharacters() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("java & spring * boot ? test");

        when(cbServerProperties.getSearchFieldsWithBoost())
                .thenReturn("title:2.0,summary:1.5");

        TotalHits totalHits = new TotalHits.Builder()
                .value(5L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getTotalCount());
    }

    @Test
    void searchDocumentsV2_shouldHandleInvalidBoostConfigFormat() throws IOException {
        // Arrange
        String esIndexName = "test-index";
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setPageNumber(0);
        searchCriteria.setPageSize(10);
        searchCriteria.setSearchString("test");

        when(cbServerProperties.getSearchFieldsWithBoost())
                .thenReturn("title,summary:1.5"); // Invalid format for first field

        TotalHits totalHits = new TotalHits.Builder()
                .value(0L)
                .relation(TotalHitsRelation.Eq)
                .build();

        HitsMetadata<Object> hitsMetadata = new HitsMetadata.Builder<>()
                .total(totalHits)
                .hits(Collections.emptyList())
                .build();

        SearchResponse<Object> mockSearchResponse = new SearchResponse.Builder<Object>()
                .took(10)
                .timedOut(false)
                .shards(s -> s.total(1).successful(1).failed(0).skipped(0))
                .hits(hitsMetadata)
                .aggregations(new HashMap<>())
                .build();

        when(elasticsearchClient.search(any(SearchRequest.class), eq(Object.class)))
                .thenReturn(mockSearchResponse);
        when(objectMapper.valueToTree(any())).thenReturn(mock(JsonNode.class));

        // Act
        SearchResult result = esUtilService.searchDocumentsV2(esIndexName, searchCriteria);

        // Assert
        assertNotNull(result);
    }

}
