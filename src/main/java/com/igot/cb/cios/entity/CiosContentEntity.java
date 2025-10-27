package com.igot.cb.cios.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;


import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.sql.Timestamp;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class CiosContentEntity implements Serializable {
    @Id
    private String contentId;
    private String externalId;
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private transient JsonNode ciosData;
    private Boolean isActive;
    private Timestamp createdOn;
    private Timestamp lastUpdatedOn;
    private String partnerId;
}