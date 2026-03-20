package com.igot.cb.knowledgecentre.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
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
@Table(name = "knowledge_category")
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeCategoryEntity {

    @Id
    @Column(name = "categoryId")
    private String categoryId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode categoryData;

    @Column(name = "created_on")
    private String createdOn;

    @Column(name = "updated_on")
    private String updatedOn;
}

