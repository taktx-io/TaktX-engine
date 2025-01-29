package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.FlowNodeInstanceTypeIdResolver;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(FlowNodeInstanceTypeIdResolver.class)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class FlowNodeInstanceDTO {
  @JsonProperty("i")
  private UUID elementInstanceId;

  @JsonProperty("p")
  private UUID parentElementInstanceId;

  @JsonProperty("e")
  private String elementId;

  @JsonProperty("c")
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
