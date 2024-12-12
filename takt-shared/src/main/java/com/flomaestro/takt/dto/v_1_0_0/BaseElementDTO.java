package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.CustomTypeIdResolver;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode
@JsonTypeInfo(use = Id.CUSTOM, property = "cls")
@JsonTypeIdResolver(CustomTypeIdResolver.class)
@NoArgsConstructor
public abstract class BaseElementDTO {
  @JsonProperty("id")
  private String id;

  @JsonProperty("pid")
  private String parentId;

  protected BaseElementDTO(String id, String parentId) {
    this.id = id;
    this.parentId = parentId;
  }
}
