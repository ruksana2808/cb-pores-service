package com.igot.cb.knowledgecentre.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.knowledgecentre.repository.KnowledgeArticlesRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeCategoryRepository;
import com.igot.cb.knowledgecentre.repository.KnowledgeSubCategoryRepository;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for Knowledge Centre operations
 * Contains common helper methods for validation, formatting, and data extraction
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeCentreUtil {

    private final KnowledgeCategoryRepository knowledgeCategoryRepository;
    private final KnowledgeSubCategoryRepository knowledgeSubCategoryRepository;
    private final KnowledgeArticlesRepository knowledgeArticlesRepository;

    /**
     * Safely extract string value from JsonNode
     * Returns null if node is null, missing, or contains null value
     *
     * @param jsonNode  The JSON node to extract from
     * @param fieldName The field name to extract
     * @return The string value or null
     */
    public String getStringValue(JsonNode jsonNode, String fieldName) {
        if (jsonNode == null || !jsonNode.has(fieldName)) {
            return null;
        }
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        String value = fieldNode.asText();
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    /**
     * Format timestamp to ISO format for Elasticsearch with IST timezone
     *
     * @param timestamp The timestamp to format
     * @return Formatted ISO timestamp string or null
     */
    public String formatTimestampForES(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        Instant instant = timestamp.toInstant();
        ZonedDateTime istDateTime = instant.atZone(ZoneId.of(Constants.TIME_ZONE));
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(istDateTime);
    }

    /**
     * Check if a category title already exists in the database
     *
     * @param title     The title to check
     * @param excludeId The ID to exclude from the check (for updates), null for create
     * @return true if duplicate exists, false otherwise
     */
    public boolean isDuplicateCategoryTitle(String title, String excludeId) {
        if (excludeId == null) {
            // For create operation - check if title exists
            return knowledgeCategoryRepository.findByTitle(title).isPresent();
        } else {
            // For update operation - check if title exists for other records
            return knowledgeCategoryRepository.findByTitleAndIdNot(title, excludeId).isPresent();
        }
    }

    /**
     * Check if a subcategory title already exists in the database
     *
     * @param title     The title to check
     * @param excludeId The ID to exclude from the check (for updates), null for create
     * @return true if duplicate exists, false otherwise
     */
    public boolean isDuplicateSubCategoryTitle(String title, String categoryId, String excludeId) {

        if (excludeId == null) {
            // Create case
            return knowledgeSubCategoryRepository
                    .findByTitleAndCategoryId(title, categoryId)
                    .isPresent();
        } else {
            // Update case
            return knowledgeSubCategoryRepository
                    .findByTitleAndCategoryIdAndIdNot(title, categoryId, excludeId)
                    .isPresent();
        }
    }

    /**
     * Check if an article title already exists in the database
     *
     * @param title     The title to check
     * @param excludeId The ID to exclude from the check (for updates), null for create
     * @return true if duplicate exists, false otherwise
     */
    public boolean isDuplicateArticleTitle(String title,
                                           String subCategoryId,
                                           String excludeId) {

        if (excludeId == null) {
            return knowledgeArticlesRepository
                    .findByTitleAndSubCategoryId(title, subCategoryId)
                    .isPresent();
        } else {
            return knowledgeArticlesRepository
                    .findByTitleAndSubCategoryIdAndIdNot(
                            title, subCategoryId, excludeId)
                    .isPresent();
        }
    }

    public ApiResponse handleInvalidType(String type, ApiResponse response) {
        response.getParams().setErrMsg(Constants.INVALID_TYPE + type + Constants.SUPPORTED_TYPES);
        response.setResponseCode(HttpStatus.BAD_REQUEST);
        return response;
    }
}

