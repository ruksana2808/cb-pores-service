package com.igot.cb.playlist.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import java.io.Serializable;
import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "playlist")
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity
public class PlayListEntity implements Serializable {

  @Id
  private String id;

  private String orgId;

  private String requestType;

  private Boolean isActive;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb")
  private transient JsonNode data;

  private Timestamp createdOn;

  private Timestamp updatedOn;

}
