package com.igot.cb.knowledgecentre.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "knowledge_subCategory")
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeSubCategoryEntity {

    @Id
    @Column(name = "subCategoryId")
    private String subCategoryId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode subCategoryData;

    @Column(name = "created_on")
    private String createdOn;

    @Column(name = "updated_on")
    private String updatedOn;
}
