package com.igot.cb.contentpartner.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.contentpartner.entity.ContentPartnerEntity;
import com.igot.cb.contentpartner.repository.ContentPartnerRepository;
import com.igot.cb.contentpartner.service.ContentPartnerService;
import com.igot.cb.playlist.util.ProjectUtil;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PayloadValidation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.sql.Timestamp;
import java.util.*;

@Service
@Slf4j
public class ContentPartnerServiceImpl implements ContentPartnerService {

    @Autowired
    private EsUtilService esUtilService;

    @Autowired
    private ContentPartnerRepository entityRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CbServerProperties cbServerProperties;

    @Autowired
    private PayloadValidation payloadValidation;

    private Logger logger = LoggerFactory.getLogger(ContentPartnerServiceImpl.class);

    @Override
    public ApiResponse createOrUpdate(JsonNode partnerDetails) {
        log.info("ContentPartnerServiceImpl::createOrUpdate:inside");
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        try {
            if (isCreate(partnerDetails)) {
                return handleCreate(partnerDetails, now);
            } else {
                return handleUpdate(partnerDetails, now);
            }
        } catch (Exception e) {
            return errorResponse(ProjectUtil.createDefaultResponse(Constants.API_PARTNER_CREATE), e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isCreate(JsonNode partnerDetails) {
        return partnerDetails.get(Constants.ID) == null;
    }

    private ApiResponse handleCreate(JsonNode partnerDetails, Timestamp now) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_CREATE);
        payloadValidation.validatePayload(Constants.PAYLOAD_VALIDATION_FILE_CONTENT_PROVIDER, partnerDetails);
        final String partnerName = partnerDetails.path(Constants.CONTENT_PARTNER_NAME).asText();
        final String partnerCode = safeText(partnerDetails, Constants.PARTNERCODE);
        Optional<String> duplicateErr = checkDuplicatesOnCreate(partnerName, partnerCode);
        if (duplicateErr.isPresent()) {
            return errorResponse(response, duplicateErr.get(), HttpStatus.BAD_REQUEST);
        }
        final String id = UUID.randomUUID().toString();
        ObjectNode dataNode = prepareCreateDataNode((ObjectNode) partnerDetails, id, now);
        ContentPartnerEntity entity = buildEntityFromCreate(partnerDetails, id, now);
        ContentPartnerEntity saved = entityRepository.save(entity);
        afterPersistCreate(saved, id);
        Map<String, Object> result = objectMapper.convertValue(saved, Map.class);
        response.setResult(result);
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    private Optional<String> checkDuplicatesOnCreate(String partnerName, String partnerCode) {
        Optional<ContentPartnerEntity> nameHit = entityRepository.findByContentPartnerName(partnerName);
        if (nameHit.isPresent()) {
            if (isNonEmpty(partnerCode) && entityRepository.findByPartnerCode(partnerCode).isPresent()) {
                return Optional.of(Constants.CONTENT_PARTNER_CODE_AND_NAME_ALREADY_PRESENT);
            }
            return Optional.of(Constants.CONTENT_PARTNER_NAME_ALREADY_PRESENT);
        }
        if (isNonEmpty(partnerCode) && entityRepository.findByPartnerCode(partnerCode).isPresent()) {
            return Optional.of(Constants.CONTENT_PARTNER_CODE_ALREADY_PRESENT);
        }
        return Optional.empty();
    }

    private ObjectNode prepareCreateDataNode(ObjectNode partnerDetails, String id, Timestamp now) {
        // ensure partnerCode field exists and base fields are set
        partnerDetails.put(Constants.PARTNERCODE, partnerDetails.path("partnerCode").asText(""));
        partnerDetails.put(Constants.ID, id);
        partnerDetails.put(Constants.IS_ACTIVE, Constants.ACTIVE_STATUS);
        partnerDetails.put(Constants.TOTAL_COURSES_COUNT, 0);
        partnerDetails.put(Constants.DRAFT_COURSES_COUNT, 0);
        partnerDetails.put(Constants.LIVE_COURSES_COUNT, 0);
        if (partnerDetails.path(Constants.IS_AUTHENTICATE).isMissingNode()) {
            partnerDetails.put(Constants.IS_AUTHENTICATE, Constants.ACTIVE_STATUS_AUTHENTICATE);
        }
        if (partnerDetails.path(Constants.PROVIDER_TIPS).isMissingNode()) {
            partnerDetails.set(Constants.PROVIDER_TIPS, partnerDetails.arrayNode());
        }
        partnerDetails.put(Constants.CREATED_ON, String.valueOf(now));
        partnerDetails.put(Constants.UPDATED_ON, String.valueOf(now));
        partnerDetails.put(Constants.DOCUMENT_UPLOADED_DATE, partnerDetails.path(Constants.DOCUMENT_UPLOADED_DATE).asText(""));
        addSearchTags(partnerDetails);
        return partnerDetails;
    }

    private ContentPartnerEntity buildEntityFromCreate(JsonNode partnerDetails, String id, Timestamp now) {
        ContentPartnerEntity e = new ContentPartnerEntity();
        e.setId(id);
        e.setCreatedOn(now);
        e.setUpdatedOn(now);
        e.setIsActive(Constants.ACTIVE_STATUS);

        // capture heavy fields into columns
        e.setTrasformContentJson(partnerDetails.get(Constants.TRANSFORM_CONTENT_JSON));
        e.setTransformProgressJson(partnerDetails.get(Constants.TRANSFORM_PROGRESS_JSON));
        e.setCertificateTemplateUrl(partnerDetails.path(Constants.CERTIFICATE_TEMPLATE_URL).asText(" "));
        e.setServiceRegistryDetails(partnerDetails.get(Constants.SERVICE_REGISTRY_DETAILS));
        e.setContentFileValidation(partnerDetails.get(Constants.CONTENT_FILE_VALIDATION));
        e.setTransformContentViaApi(partnerDetails.get(Constants.TRANSFORM_CONTENT_VIA_API));
        e.setTransformProgressViaApi(partnerDetails.get(Constants.TRANSFORM_PROGRESS_VIA_API));
        // remove the heavy fields from data JSON
        ObjectNode data = ((ObjectNode) partnerDetails).deepCopy();
        stripHeavyFields(data, true);
        e.setData(data);
        return e;
    }

    private void afterPersistCreate(ContentPartnerEntity saved, String id) {
        Map<String, Object> map = objectMapper.convertValue(saved.getData(), Map.class);
        esUtilService.addDocument(
                Constants.CONTENT_PROVIDER_INDEX_NAME, Constants.INDEX_TYPE, id, map, cbServerProperties.getElasticContentJsonPath());

        Map<String, Object> result = objectMapper.convertValue(saved, Map.class);
        cacheService.putCache(saved.getId(), result);

        JsonNode codeNode = saved.getData().path(Constants.PARTNERCODE);
        if (!codeNode.isMissingNode()) {
            log.info("during content partner create Deleting cache for partner code {}", codeNode.asText());
            cacheService.deleteCache(codeNode.asText());
        }
        log.info("Content partner created");
    }

    private ApiResponse handleUpdate(JsonNode partnerDetails, Timestamp now) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_UPDATE);

        JsonNode data = partnerDetails.get(Constants.DATA);
        payloadValidation.validatePayload(Constants.PAYLOAD_VALIDATION_FILE_CONTENT_PROVIDER, data);

        final String existingId = partnerDetails.path(Constants.ID).asText();
        Optional<ContentPartnerEntity> existingOpt = entityRepository.findById(existingId);
        if (existingOpt.isEmpty()) {
            return errorResponse(response, Constants.DATA_NOT_PRESENT, HttpStatus.BAD_REQUEST);
        }

        String partnerName = partnerDetails.path(Constants.DATA).path(Constants.CONTENT_PARTNER_NAME).asText();
        if (isDuplicateNameForAnother(partnerName, existingId)) {
            return errorResponse(response, Constants.CONTENT_PARTNER_NAME_ALREADY_PRESENT, HttpStatus.BAD_REQUEST);
        }

        ContentPartnerEntity entity = existingOpt.get();
        applyUpdateColumns(entity, partnerDetails, now);

        ObjectNode dataNode = extractUpdateData((ObjectNode) partnerDetails, entity, now);
        addSearchTags(dataNode);
        entity.setData(dataNode);

        ContentPartnerEntity updated = entityRepository.save(entity);
        if (ObjectUtils.isEmpty(updated)) {
            // nothing saved; return as-is with 200? keep consistent with original—no special handling
            return response;
        }

        afterPersistUpdate(updated, existingId);

        Map<String, Object> result = objectMapper.convertValue(updated, Map.class);
        response.setResult(result);
        response.setResponseCode(HttpStatus.OK);
        return response;
    }

    private boolean isDuplicateNameForAnother(String partnerName, String selfId) {
        return entityRepository.findByContentPartnerName(partnerName)
                .filter(e -> !e.getId().equals(selfId))
                .isPresent();
    }

    private void applyUpdateColumns(ContentPartnerEntity entity, JsonNode partnerDetails, Timestamp now) {
        entity.setUpdatedOn(now);
        entity.setIsActive(Constants.ACTIVE_STATUS);
        entity.setTrasformContentJson(partnerDetails.get(Constants.TRANSFORM_CONTENT_JSON));
        entity.setTransformProgressJson(partnerDetails.get(Constants.TRANSFORM_PROGRESS_JSON));
        entity.setCertificateTemplateUrl(partnerDetails.path(Constants.CERTIFICATE_TEMPLATE_URL).asText(" "));
        entity.setServiceRegistryDetails(partnerDetails.get(Constants.SERVICE_REGISTRY_DETAILS));
        entity.setContentFileValidation(partnerDetails.get(Constants.CONTENT_FILE_VALIDATION));
        entity.setTransformContentViaApi(partnerDetails.get(Constants.TRANSFORM_CONTENT_VIA_API));
        entity.setTransformProgressViaApi(partnerDetails.get(Constants.TRANSFORM_PROGRESS_VIA_API));
    }

    private ObjectNode extractUpdateData(ObjectNode partnerDetails, ContentPartnerEntity existing, Timestamp now) {
        ObjectNode rootCopy = partnerDetails.deepCopy();
        stripHeavyFields(rootCopy, false);
        ObjectNode dataNode = (ObjectNode) rootCopy.remove(Constants.DATA);
        dataNode.put(Constants.CREATED_ON, String.valueOf(existing.getCreatedOn()));
        dataNode.put(Constants.UPDATED_ON, String.valueOf(now));
        dataNode.set(Constants.PARTNERCODE, existing.getData().get(Constants.PARTNERCODE));
        rootCopy.put(Constants.DOCUMENT_UPLOADED_DATE, rootCopy.path(Constants.DOCUMENT_UPLOADED_DATE).asText(""));
        dataNode.put(Constants.IS_ACTIVE, Constants.ACTIVE_STATUS);
        if (dataNode.path(Constants.IS_AUTHENTICATE).isMissingNode()) {
            dataNode.set(Constants.IS_AUTHENTICATE, existing.getData().get(Constants.IS_AUTHENTICATE));
        }
        if (dataNode.path(Constants.PROVIDER_TIPS).isMissingNode()) {
            dataNode.set(Constants.PROVIDER_TIPS, rootCopy.arrayNode());
        }
        copyIfMissing(dataNode, existing.getData(), Constants.TOTAL_COURSES_COUNT);
        copyIfMissing(dataNode, existing.getData(), Constants.DRAFT_COURSES_COUNT);
        copyIfMissing(dataNode, existing.getData(), Constants.LIVE_COURSES_COUNT);

        return dataNode;
    }

    private void afterPersistUpdate(ContentPartnerEntity updated, String id) {
        Map<String, Object> jsonMap =
                objectMapper.convertValue(updated.getData(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        esUtilService.updateDocument(
                Constants.CONTENT_PROVIDER_INDEX_NAME, Constants.INDEX_TYPE, id, jsonMap, cbServerProperties.getElasticContentJsonPath());

        Map<String, Object> result = objectMapper.convertValue(updated, Map.class);
        cacheService.putCache(updated.getId(), result);

        JsonNode codeNode = updated.getData().path(Constants.PARTNERCODE);
        if (!codeNode.isMissingNode()) {
            log.info("deleting the content partner from cache");
            cacheService.deleteCache(codeNode.asText());
        }
        log.info("updated the content partner");
    }

    private void stripHeavyFields(ObjectNode node, boolean alsoRemoveId) {
        // fields stored in dedicated columns, not in the JSON blob
        node.remove(Constants.TRANSFORM_CONTENT_JSON);
        node.remove(Constants.TRANSFORM_PROGRESS_JSON);
        node.remove(Constants.CERTIFICATE_TEMPLATE_URL);
        node.remove(Constants.CONTENT_FILE_VALIDATION);
        node.remove(Constants.TRANSFORM_CONTENT_VIA_API);
        node.remove(Constants.TRANSFORM_PROGRESS_VIA_API);
        node.remove(Constants.SERVICE_REGISTRY_DETAILS);
        if (!alsoRemoveId) {
            node.remove(Constants.ID); // on update flow we explicitly drop id from data
        }
    }

    private boolean isNonEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private String safeText(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    private void copyIfMissing(ObjectNode target, JsonNode source, String field) {
        if (target.path(field).isMissingNode()) {
            target.set(field, source.get(field));
        }
    }

    private ApiResponse errorResponse(ApiResponse base, String msg, HttpStatus status) {
        base.getParams().setErrMsg(msg);
        base.getParams().setStatus(Constants.FAILED);
        base.setResponseCode(status);
        return base;
    }


    private JsonNode addSearchTags(JsonNode formattedData) {
        List<String> searchTags = new ArrayList<>();
        searchTags.add(formattedData.get("contentPartnerName").textValue().toLowerCase());
        ArrayNode searchTagsArray = objectMapper.valueToTree(searchTags);
        ((ObjectNode) formattedData).put("searchTags", searchTagsArray);
        return formattedData;
    }


    @Override
    public ApiResponse read(String id) {
        log.info("ContentPartnerServiceImpl::read:reading information about the content partner");
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_READ);
        if (StringUtils.isEmpty(id)) {
            response.getParams().setErrMsg(Constants.ID_NOT_FOUND);
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("Record coming from redis cache");
                response.setResponseCode(HttpStatus.OK);
                response.setResult(objectMapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() {}));
            } else {
                Optional<ContentPartnerEntity> entityOptional = entityRepository.findByIdAndIsActive(id, true);
                if (entityOptional.isPresent()) {
                    ContentPartnerEntity entity = entityOptional.get();
                    cacheService.putCache(id, entity);
                    log.info("Record coming from postgres db");
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult(objectMapper.convertValue(entity, Map.class));
                } else {
                    response.getParams().setErrMsg(Constants.INVALID_ID);
                    response.getParams().setStatus(Constants.FAILED);
                    response.setResponseCode(HttpStatus.BAD_REQUEST);
                }
            }
        } catch (Exception e) {
            log.error("error while processing", e);
            response.getParams().setErrMsg(e.getMessage());
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @Override
    public ApiResponse searchEntity(SearchCriteria searchCriteria) {
        log.info("ContentPartnerServiceImpl::searchEntity:searching the content partner");
        String searchString = searchCriteria.getSearchString();
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_PARTNER_SEARCH);
        if (searchString != null && searchString.length() < 2) {
            response.getParams().setErrMsg("Minimum 3 characters are required to search");
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.BAD_REQUEST);
        }
        try {
            SearchResult searchResult =
                    esUtilService.searchDocuments(Constants.CONTENT_PROVIDER_INDEX_NAME, searchCriteria);
            Map<String, Object> jsonMap =
                    objectMapper.convertValue(searchResult, new TypeReference<Map<String, Object>>() {
                    });
            response.setResult(jsonMap);
            response.setResponseCode(HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error while processing to search", e);
            response.getParams().setErrMsg(e.getMessage());
            response.getParams().setStatus(Constants.FAILED);
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @Override
    public ApiResponse delete(String id) {
        log.info("ContentPartnerServiceImpl::delete:deleting the content partner");
        ApiResponse response=ProjectUtil.createDefaultResponse(Constants.API_PARTNER_DELETE);
        try {
            if (StringUtils.isNotEmpty(id)) {
                Optional<ContentPartnerEntity> entityOptional = entityRepository.findByIdAndIsActive(id,true);
                if (entityOptional.isPresent()) {
                    ContentPartnerEntity josnEntity = entityOptional.get();
                    Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                    josnEntity.setUpdatedOn(currentTime);
                    josnEntity.setIsActive(Constants.ACTIVE_STATUS_FALSE);
                    ((ObjectNode) josnEntity.getData()).put(Constants.IS_ACTIVE, Constants.ACTIVE_STATUS_FALSE);
                    entityRepository.save(josnEntity);
                    Map<String, Object> map = objectMapper.convertValue(josnEntity.getData(), Map.class);
                    esUtilService.addDocument(Constants.CONTENT_PROVIDER_INDEX_NAME, Constants.INDEX_TYPE, id, map, cbServerProperties.getElasticContentJsonPath());
                    cacheService.deleteCache(id);
                    Map<String,Object> map1=new HashMap<>();
                    map1.put(id,Constants.DELETED_SUCCESSFULLY);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult(map1);
                } else {
                    response.setResponseCode(HttpStatus.BAD_REQUEST);
                    response.getParams().setErrMsg(Constants.CONTENT_PARTNER_NOT_FOUND);
                }
            } else {
                response.setResponseCode(HttpStatus.BAD_REQUEST);
                response.getParams().setErrMsg(Constants.INVALID_ID);
            }
        } catch (Exception e) {
            log.error("Error deleting Entity with ID " + id + " " + e.getMessage());
            response.setResponseCode(HttpStatus.BAD_REQUEST);
            response.getParams().setErrMsg("Error deleting Entity with ID " + id + " " + e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse getContentDetailsByPartnerCode(String partnercode) {
        log.info("CiosContentService:: ContentPartnerEntity: getContentDetailsByPartnerName {}",partnercode);
        try {
            ApiResponse response=ProjectUtil.createDefaultResponse(Constants.API_PARTNER_READ);
            ContentPartnerEntity entity=null;
            String cachedJson = cacheService.getCache(partnercode);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("Record coming from redis cache");
                response.setResponseCode(HttpStatus.OK);
                response.setResult(objectMapper.readValue(cachedJson, new TypeReference<Map<String, Object>>() {}));
            } else {
                Optional<ContentPartnerEntity> entityOptional = entityRepository.findByPartnerCode(partnercode);
                if (entityOptional.isPresent()) {
                    log.info("Record coming from postgres db");
                    entity = entityOptional.get();
                    cacheService.putCache(partnercode,entity);
                    response.setResponseCode(HttpStatus.OK);
                    response.setResult(objectMapper.convertValue(entity, Map.class));
                } else {
                    response.getParams().setErrMsg("Invalid name");
                    response.getParams().setStatus(Constants.FAILED);
                    response.setResponseCode(HttpStatus.BAD_REQUEST);
                }
            }
            return response;
        } catch (Exception e) {
            log.error("error while processing", e);
        }
        return null;
    }
}
