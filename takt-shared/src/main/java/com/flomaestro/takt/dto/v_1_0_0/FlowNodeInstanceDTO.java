package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.CustomTypeIdResolver;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "cls")
@JsonTypeIdResolver(CustomTypeIdResolver.class)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class FlowNodeInstanceDTO {
  @JsonProperty("eii")
  private UUID elementInstanceId;

  @JsonProperty("pei")
  private UUID parentElementInstanceId;

  @JsonProperty("ei")
  private String elementId;

  @JsonProperty("pc")
  private int passedCnt;

  protected FlowNodeInstanceDTO(UUID elementInstanceId, String elementId, int passedCnt) {
    this.elementInstanceId = elementInstanceId;
    this.elementId = elementId;
    this.passedCnt = passedCnt;
  }

  @JsonIgnore
  public boolean isTerminated() {
    return false;
  }

  @JsonIgnore
  public boolean isFailed() {
    return false;
  }
}
