package com.igot.cb.knowledgecentre.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.authentication.util.AccessTokenValidator;
import com.igot.cb.knowledgecentre.entity.KnowledgeArticleEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeCategoryEntity;
import com.igot.cb.knowledgecentre.entity.KnowledgeSubCategoryEntity;
import com.igot.cb.knowledgecentre.repository.KnowledgeArticlesRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeCategoryRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeSubCategoryRepository;
import com.igot.cb.knowledgecentre.service.KnowledgeService;
import com.igot.cb.knowledgecentre.service.UserService;
import com.igot.cb.knowledgecentre.util.KnowledgeCentreUtil;
import com.igot.cb.playlist.util.ProjectUtil;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final KnowledgeCategoryRepository knowledgeCategoryRepository;
    private final KnowledgeSubCategoryRepository knowledgeSubCategoryRepository;
    private final KnowledgeArticlesRepository knowledgeArticlesRepository;
    private final PayloadValidation payloadValidation;
    private final EsUtilService esUtilService;
    private final ObjectMapper objectMapper;
    private final CbServerProperties cbServerProperties;
    private final AccessTokenValidator accessTokenValidator;
    private final RedisTemplate<String, SearchResult> redisTemplate;
    private final KnowledgeCentreUtil knowledgeCentreUtil;
    private final UserService userService;
    private final CacheService cacheService;

    @Override
    public ApiResponse createCategory(JsonNode categoryDto, String token) {
        log.info("KnowledgeServiceImpl.createCategory inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CATEGORY_CREATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        ((ObjectNode) categoryDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.CATEGORY_FILE_JSON, categoryDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        String title = categoryDto.path(Constants.TITLE).asText("");
        if (StringUtils.isNotEmpty(title) && knowledgeCentreUtil.isDuplicateCategoryTitle(title, null)) {
            log.error(Constants.DUPLICATE_CATEGORY_TITLE, title);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        String id = UUID.randomUUID().toString();
        ((ObjectNode) categoryDto).put(Constants.CATEGORY_ID, id);
        ((ObjectNode) categoryDto).put(Constants.CREATED_BY, userId);
        ((ObjectNode) categoryDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) categoryDto).put(Constants.CREATED_ON, isoTimestamp);
        ((ObjectNode) categoryDto).put(Constants.UPDATED_ON, isoTimestamp);
        KnowledgeCategoryEntity knowledgeCategoryEntity = new KnowledgeCategoryEntity();
        knowledgeCategoryEntity.setCategoryId(id);
        knowledgeCategoryEntity.setCategoryData(categoryDto);
        knowledgeCategoryEntity.setCreatedOn(isoTimestamp);
        knowledgeCategoryEntity.setUpdatedOn(isoTimestamp);
        knowledgeCategoryRepository.save(knowledgeCategoryEntity);
        Map<String, Object> map = objectMapper.convertValue(knowledgeCategoryEntity.getCategoryData(), new TypeReference<>() {
        });
        esUtilService.addDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.CATEGORY_CREATED);
        response.setResult(objectMapper.convertValue(knowledgeCategoryEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse updateCategory(String id, JsonNode categoryDto, String token) {
        log.info("KnowledgeServiceImpl.updateCategory inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CATEGORY_UPDATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }
        ((ObjectNode) categoryDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.CATEGORY_FILE_JSON, categoryDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        KnowledgeCategoryEntity existingEntity = knowledgeCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        String title = categoryDto.path(Constants.TITLE).asText("");
        if (StringUtils.isNotBlank(title) && knowledgeCentreUtil.isDuplicateCategoryTitle(title, id)) {
            log.error(Constants.DUPLICATE_CATEGORY_TITLE, title);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        ((ObjectNode) categoryDto).put(Constants.CATEGORY_ID, id);
        ((ObjectNode) categoryDto).put(Constants.CREATED_BY, existingEntity.getCategoryData().get(Constants.CREATED_BY).asText());
        ((ObjectNode) categoryDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) categoryDto).put(Constants.CREATED_ON, existingEntity.getCategoryData().get(Constants.CREATED_ON).asText());
        ((ObjectNode) categoryDto).put(Constants.UPDATED_ON, isoTimestamp);
        existingEntity.setCategoryData(categoryDto);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeCategoryRepository.save(existingEntity);
        Map<String, Object> map = objectMapper.convertValue(existingEntity.getCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.CATEGORY_UPDATED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse publishCategory(String id, String token) {
        log.info("KnowledgeServiceImpl.publishCategory inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CATEGORY_PUBLISH);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeCategoryEntity existingEntity = knowledgeCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        JsonNode categoryData = existingEntity.getCategoryData();
        ((ObjectNode) categoryData).put(Constants.STATUS, Constants.PUBLISHED_KEY);
        payloadValidation.validatePayload(Constants.CATEGORY_FILE_JSON, categoryData);
        ((ObjectNode) categoryData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) categoryData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setCategoryData(categoryData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeCategoryRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.CATEGORY_PUBLISHED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse deleteCategory(String id, String token) {
        log.info("KnowledgeServiceImpl.deleteCategory inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_CATEGORY_DELETE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeCategoryEntity existingEntity = knowledgeCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        // Archive instead of delete - update status to ARCHIVED
        JsonNode categoryData = existingEntity.getCategoryData();
        ((ObjectNode) categoryData).put(Constants.STATUS, Constants.ARCHIVED_KEY);
        ((ObjectNode) categoryData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) categoryData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setCategoryData(categoryData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeCategoryRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.CATEGORY_DELETED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse createSubCategory(JsonNode subCategoryDto, String token) {
        log.info("KnowledgeServiceImpl.createSubCategory inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SUB_CATEGORY_CREATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }
        ((ObjectNode) subCategoryDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.SUB_CATEGORY_FILE_JSON, subCategoryDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        String title = subCategoryDto.path(Constants.TITLE).asText("");
        if (StringUtils.isNotBlank(title) && knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, subCategoryDto.path(Constants.CATEGORYID).asText(), null)) {
            log.error("Duplicate category title: {}", title);
            ProjectUtil.errorResponse(response, Constants.CATEGORY_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        String id = UUID.randomUUID().toString();
        ((ObjectNode) subCategoryDto).put(Constants.SUB_CATEGORY_ID, id);
        ((ObjectNode) subCategoryDto).put(Constants.CREATED_BY, userId);
        ((ObjectNode) subCategoryDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) subCategoryDto).put(Constants.CREATED_ON, isoTimestamp);
        ((ObjectNode) subCategoryDto).put(Constants.UPDATED_ON, isoTimestamp);
        KnowledgeSubCategoryEntity knowledgeCategoryEntity = new KnowledgeSubCategoryEntity();
        knowledgeCategoryEntity.setSubCategoryId(id);
        knowledgeCategoryEntity.setSubCategoryData(subCategoryDto);
        knowledgeCategoryEntity.setCreatedOn(isoTimestamp);
        knowledgeCategoryEntity.setUpdatedOn(isoTimestamp);
        knowledgeSubCategoryRepository.save(knowledgeCategoryEntity);
        Map<String, Object> map = objectMapper.convertValue(knowledgeCategoryEntity.getSubCategoryData(), new TypeReference<>() {
        });
        esUtilService.addDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.SUB_CATEGORY_CREATED);
        response.setResult(objectMapper.convertValue(knowledgeCategoryEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse updateSubCategory(String id, JsonNode subCategoryDto, String token) {
        log.info("KnowledgeServiceImpl.updateSubCategory inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SUB_CATEGORY_UPDATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }
        ((ObjectNode) subCategoryDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.SUB_CATEGORY_FILE_JSON, subCategoryDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        KnowledgeSubCategoryEntity existingEntity = knowledgeSubCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.SUB_CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.SUB_CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        String title = subCategoryDto.path(Constants.TITLE).asText("");
        if (StringUtils.isNotBlank(title) && knowledgeCentreUtil.isDuplicateSubCategoryTitle(title, subCategoryDto.path(Constants.CATEGORYID).asText(), id)) {
            log.error("Duplicate subcategory title: {}", title);
            ProjectUtil.errorResponse(response, Constants.SUB_CATEGORY_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        ((ObjectNode) subCategoryDto).put(Constants.SUB_CATEGORY_ID, id);
        ((ObjectNode) subCategoryDto).put(Constants.CREATED_BY, existingEntity.getSubCategoryData().get(Constants.CREATED_BY).asText());
        ((ObjectNode) subCategoryDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) subCategoryDto).put(Constants.CREATED_ON, existingEntity.getSubCategoryData().get(Constants.CREATED_ON).asText());
        ((ObjectNode) subCategoryDto).put(Constants.UPDATED_ON, isoTimestamp);
        existingEntity.setSubCategoryData(subCategoryDto);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeSubCategoryRepository.save(existingEntity);
        Map<String, Object> map = objectMapper.convertValue(existingEntity.getSubCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.SUB_CATEGORY_UPDATED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse publishSubCategory(String id, String token) {
        log.info("KnowledgeServiceImpl.publishSubCategory inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SUB_CATEGORY_PUBLISH);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeSubCategoryEntity existingEntity = knowledgeSubCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.SUB_CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.SUB_CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        JsonNode subCategoryData = existingEntity.getSubCategoryData();
        ((ObjectNode) subCategoryData).put(Constants.STATUS, Constants.PUBLISHED_KEY);
        payloadValidation.validatePayload(Constants.SUB_CATEGORY_FILE_JSON, subCategoryData);
        ((ObjectNode) subCategoryData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) subCategoryData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setSubCategoryData(subCategoryData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeSubCategoryRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getSubCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.SUB_CATEGORY_PUBLISHED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse deleteSubCategory(String id, String token) {
        log.info("KnowledgeServiceImpl.deleteSubCategory inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SUB_CATEGORY_DELETE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeSubCategoryEntity existingEntity = knowledgeSubCategoryRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.SUB_CATEGORY_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.SUB_CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        // Archive instead of delete - update status to ARCHIVED
        JsonNode subCategoryData = existingEntity.getSubCategoryData();
        ((ObjectNode) subCategoryData).put(Constants.STATUS, Constants.ARCHIVED_KEY);
        ((ObjectNode) subCategoryData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) subCategoryData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setSubCategoryData(subCategoryData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeSubCategoryRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getSubCategoryData(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.SUB_CATEGORY_DELETED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse createArticle(JsonNode articleDto, String token) {
        log.info("KnowledgeServiceImpl.createArticle inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ARTICLE_CREATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }
        ((ObjectNode) articleDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.ARTICLE_FILE_JSON, articleDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        String title = articleDto.path(Constants.TITLE).asText("");
        if (StringUtils.isNotBlank(title) && knowledgeCentreUtil.isDuplicateArticleTitle(title, articleDto.path(Constants.SUBCATEGORYID).asText(), null)) {
            log.error(Constants.DUPLICATE_ARTICLE_TITLE, title);
            ProjectUtil.errorResponse(response, Constants.ARTICLE_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        String id = UUID.randomUUID().toString();
        ((ObjectNode) articleDto).put(Constants.ARTICLE_ID, id);
        ((ObjectNode) articleDto).put(Constants.CREATED_BY, userId);
        ((ObjectNode) articleDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) articleDto).put(Constants.CREATED_ON, isoTimestamp);
        ((ObjectNode) articleDto).put(Constants.UPDATED_ON, isoTimestamp);
        KnowledgeArticleEntity knowledgeCategoryEntity = new KnowledgeArticleEntity();
        knowledgeCategoryEntity.setArticleId(id);
        knowledgeCategoryEntity.setArticles(articleDto);
        knowledgeCategoryEntity.setCreatedOn(isoTimestamp);
        knowledgeCategoryEntity.setUpdatedOn(isoTimestamp);
        knowledgeArticlesRepository.save(knowledgeCategoryEntity);
        Map<String, Object> map = objectMapper.convertValue(knowledgeCategoryEntity.getArticles(), new TypeReference<>() {
        });
        esUtilService.addDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.ARTICLE_CREATED);
        response.setResult(objectMapper.convertValue(knowledgeCategoryEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse updateArticle(String id, JsonNode articleDto, String token) {
        log.info("KnowledgeServiceImpl.updateArticle inside method");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ARTICLE_UPDATE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }
        ((ObjectNode) articleDto).put(Constants.STATUS, Constants.DRAFT_KEY);
        payloadValidation.validatePayload(Constants.ARTICLE_FILE_JSON, articleDto);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);
        KnowledgeArticleEntity existingEntity = knowledgeArticlesRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.ARTICLE_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.ARTICLE_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        String title = articleDto.get(Constants.TITLE).asText("");
        if (StringUtils.isNotBlank(title) && knowledgeCentreUtil.isDuplicateArticleTitle(title, articleDto.path(Constants.SUBCATEGORYID).asText(), id)) {
            log.error(Constants.DUPLICATE_ARTICLE_TITLE, title);
            ProjectUtil.errorResponse(response, Constants.ARTICLE_TITLE_EXISTS, HttpStatus.BAD_REQUEST);
            return response;
        }

        ((ObjectNode) articleDto).put(Constants.ARTICLE_ID, id);
        ((ObjectNode) articleDto).put(Constants.CREATED_BY, existingEntity.getArticles().get(Constants.CREATED_BY).asText());
        ((ObjectNode) articleDto).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) articleDto).put(Constants.CREATED_ON, existingEntity.getArticles().get(Constants.CREATED_ON).asText());
        ((ObjectNode) articleDto).put(Constants.UPDATED_ON, isoTimestamp);
        existingEntity.setArticles(articleDto);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeArticlesRepository.save(existingEntity);
        Map<String, Object> map = objectMapper.convertValue(existingEntity.getArticles(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);
        log.info(Constants.ARTICLE_UPDATED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse publishArticle(String id, String token) {
        log.info("KnowledgeServiceImpl.publishArticle inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ARTICLE_PUBLISH);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeArticleEntity existingEntity = knowledgeArticlesRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.ARTICLE_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.ARTICLE_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        JsonNode articleData = existingEntity.getArticles();
        ((ObjectNode) articleData).put(Constants.STATUS, Constants.PUBLISHED_KEY);
        payloadValidation.validatePayload(Constants.ARTICLE_FILE_JSON, articleData);
        ((ObjectNode) articleData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) articleData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setArticles(articleData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeArticlesRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getArticles(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.ARTICLE_PUBLISHED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse deleteArticle(String id, String token) {
        log.info("KnowledgeServiceImpl.deleteArticle inside method for id: {}", id);
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_ARTICLE_DELETE);
        String userId = accessTokenValidator.verifyUserToken(token);
        if (userId.equalsIgnoreCase(Constants.UNAUTHORIZED)) {
            ProjectUtil.errorResponse(response, Constants.UNAUTHORIZED, HttpStatus.UNAUTHORIZED);
            return response;
        }

        KnowledgeArticleEntity existingEntity = knowledgeArticlesRepository.findById(id).orElse(null);
        if (existingEntity == null) {
            log.error(Constants.ARTICLE_NOT_FOUND);
            ProjectUtil.errorResponse(response, Constants.ARTICLE_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        String isoTimestamp = knowledgeCentreUtil.formatTimestampForES(currentTime);

        JsonNode articleData = existingEntity.getArticles();
        ((ObjectNode) articleData).put(Constants.STATUS, Constants.ARCHIVED_KEY);
        ((ObjectNode) articleData).put(Constants.UPDATED_BY, userId);
        ((ObjectNode) articleData).put(Constants.UPDATED_ON, isoTimestamp);

        existingEntity.setArticles(articleData);
        existingEntity.setUpdatedOn(isoTimestamp);
        knowledgeArticlesRepository.save(existingEntity);

        Map<String, Object> map = objectMapper.convertValue(existingEntity.getArticles(), new TypeReference<>() {
        });
        esUtilService.updateDocument(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, Constants.INDEX_TYPE, id, map, Constants.KNOWLEDGE_ES_FILE_JSON);

        log.info(Constants.ARTICLE_DELETED);
        response.setResult(objectMapper.convertValue(existingEntity, new TypeReference<>() {
        }));
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    @Override
    public ApiResponse searchEntity(SearchCriteria searchCriteria) {
        log.info("KnowledgeServiceImpl::searchEntity: searching knowledge centre entities");
        String searchString = searchCriteria.getSearchString();
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_SEARCH);
        if (searchString != null && searchString.length() < 2) {
            response.getParams().setErrMsg(Constants.SEARCH_MIN_LENGTH_ERROR_MESSAGE);
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            HashMap<String, Object> filterCriteriaMap = searchCriteria.getFilterCriteriaMap();
            if (filterCriteriaMap == null) {
                filterCriteriaMap = new HashMap<>();
                searchCriteria.setFilterCriteriaMap(filterCriteriaMap);
            }
            filterCriteriaMap.put(Constants.STATUS, Constants.PUBLISHED_KEY);
            filterCriteriaMap.put(Constants.SHOW_UNDER_DEVELOPER_DOC, Constants.ACTIVE_STATUS);
            filterCriteriaMap.put(Constants.IS_PUBLIC, Boolean.TRUE);
            SearchResult cachedResult = redisTemplate.opsForValue()
                    .get(generateRedisJwtTokenKey(searchCriteria));

            SearchResult searchResult;
            if (cachedResult != null) {
                log.info("KnowledgeServiceImpl::searchEntity: search result fetched from redis cache");
                searchResult = cachedResult;
            } else {
                searchResult = esUtilService.searchDocumentsV2(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, searchCriteria);
                redisTemplate.opsForValue()
                        .set(generateRedisJwtTokenKey(searchCriteria), searchResult, cbServerProperties.getSearchResultRedisTtl(), TimeUnit.SECONDS);
                log.info("KnowledgeServiceImpl::searchEntity: search result stored in redis cache");
            }
            Map<String, Object> jsonMap =
                    objectMapper.convertValue(searchResult, new TypeReference<>() {
                    });
            response.setResult(jsonMap);
            response.setResponseCode(HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error while processing to search", e);
            response.getParams().setErrMsg(e.getMessage());
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @Override
    public ApiResponse spvSearchEntity(SearchCriteria searchCriteria) {
        log.info("KnowledgeServiceImpl::spvSearchEntity: searching knowledge centre entities");
        String searchString = searchCriteria.getSearchString();
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_SEARCH);
        if (searchString != null && searchString.length() < 2) {
            response.getParams().setErrMsg(Constants.SEARCH_MIN_LENGTH_ERROR_MESSAGE);
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            return response;
        }
        try {
            SearchResult searchResult = esUtilService.searchDocumentsV2(Constants.KNOWLEDGE_CENTRE_INDEX_NAME, searchCriteria);
            Map<String, Object> jsonMap = objectMapper.convertValue(searchResult, new TypeReference<Map<String, Object>>() {}
            );
            List<Map<String, Object>> resultList = (List<Map<String, Object>>) jsonMap.get(Constants.DATA);
            Set<String> userListWithPrefix = new HashSet<>();
            Set<String> categoryIds = new HashSet<>();
            for (Map<String, Object> entity : resultList) {
                if (entity.get(Constants.CREATED_BY) != null) {
                    userListWithPrefix.add(Constants.BASIC_PROFILE_CACHE_PREFIX + entity.get(Constants.CREATED_BY));
                }
                if (entity.get(Constants.UPDATED_BY) != null) {
                    userListWithPrefix.add(Constants.BASIC_PROFILE_CACHE_PREFIX + entity.get(Constants.UPDATED_BY));
                }
                if (entity.get(Constants.CATEGORYID) != null) {
                    categoryIds.add(entity.get(Constants.CATEGORYID).toString());
                }
            }
            List<Object> userList = fetchUserDetails(userListWithPrefix);
            List<Object> categoryDetails = fetchCategoryDetails(categoryIds);
            jsonMap.put(Constants.USER_DETAILS, userList);
            jsonMap.put(Constants.CATEGORY_DETAILS, categoryDetails);
            response.setResult(jsonMap);
            response.setResponseCode(HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error while processing to search", e);
            response.getParams().setErrMsg(e.getMessage());
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    private List<Object> fetchUserDetails(Set<String> userListWithPrefix) {
        if (userListWithPrefix.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> userList = new ArrayList<>();
        Map<String, Object> userInfoMap = new HashMap<>();
        List<String> redisKeys = new ArrayList<>(userListWithPrefix);
        // Fetch users from Redis
        for (String key : redisKeys) {
            String cachedUser = cacheService.getCache(key);
            if (cachedUser == null) {
                continue;
            }
            try {
                JsonNode userNode = objectMapper.readTree(cachedUser);
                Map<String, Object> user = objectMapper.convertValue(userNode, new TypeReference<>() {});
                if (MapUtils.isEmpty(user) || !user.containsKey(Constants.USER_ID_KEY)) {
                    continue;
                }
                userList.add(user);
                userInfoMap.put(key, user);
            } catch (Exception e) {
                log.error("Error parsing cached user data for key: {}", key, e);
            }
        }
        // Identify missing users
        List<String> missingUserIds = new ArrayList<>();
        for (String key : redisKeys) {
            if (!userInfoMap.containsKey(key)) {
                missingUserIds.add(key.substring(Constants.BASIC_PROFILE_CACHE_PREFIX.length()));
            }
        }
        if (!missingUserIds.isEmpty()) {
            processCassandraUsers(missingUserIds, userList);
        }
        return userList;
    }

    private void processCassandraUsers(List<String> missingUserIds, List<Object> userList) {
        List<Object> cassandraUsers = userService.fetchUserFromPrimary(missingUserIds);
        if (CollectionUtils.isEmpty(cassandraUsers)) {
            return;
        }
        for (Object obj : cassandraUsers) {
            Map<String, Object> user = (Map<String, Object>) obj;
            Object userId = MapUtils.getObject(user, Constants.USER_ID_KEY);
            if (userId == null) {
                continue;
            }
            userList.add(user);
        }
    }

    private List<Object> fetchCategoryDetails(Set<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<KnowledgeCategoryEntity> categories = knowledgeCategoryRepository.findAllById(categoryIds);
        List<Object> categoryList = new ArrayList<>();
        for (KnowledgeCategoryEntity category : categories) {
            Map<String, Object> categoryMap = new HashMap<>();
            String title = null;
            if (category.getCategoryData() != null && category.getCategoryData().has(Constants.TITLE)) {
                title = category.getCategoryData().get(Constants.TITLE).asText();
            }
            categoryMap.put(Constants.ID, category.getCategoryId());
            categoryMap.put(Constants.TITLE, title);
            categoryList.add(categoryMap);
        }
        return categoryList;
    }

    private String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create()
                        .withClaim(Constants.REQUEST_PAYLOAD, reqJsonString)
                        .sign(Algorithm.HMAC256(cbServerProperties.getJwtSecretKey()));
            } catch (JsonProcessingException e) {
                log.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }
}