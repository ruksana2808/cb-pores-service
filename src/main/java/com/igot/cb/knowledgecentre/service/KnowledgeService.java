package com.igot.cb.knowledgecentre.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;

public interface KnowledgeService {

    ApiResponse createCategory(JsonNode categoryDto, String token);

    ApiResponse updateCategory(String id, JsonNode categoryDto, String token);

    ApiResponse publishCategory(String id, String token);

    ApiResponse deleteCategory(String id, String token);

    ApiResponse createSubCategory(JsonNode subCategoryDto, String token);

    ApiResponse updateSubCategory(String id, JsonNode subCategoryDto, String token);

    ApiResponse publishSubCategory(String id, String token);

    ApiResponse deleteSubCategory(String id, String token);

    ApiResponse createArticle(JsonNode articlesDto, String token);

    ApiResponse updateArticle(String id, JsonNode articlesDto, String token);

    ApiResponse publishArticle(String id, String token);

    ApiResponse deleteArticle(String id, String token);

    ApiResponse searchEntity(SearchCriteria searchCriteria);

    ApiResponse spvSearchEntity(SearchCriteria searchCriteria);
}
