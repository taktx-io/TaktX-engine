package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.FlowNodeInstanceTypeIdResolver;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(FlowNodeInstanceTypeIdResolver.class)
@JsonInclude(Include.NON_NULL)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class FlowNodeInstanceDTO {
  @JsonProperty("i")
  private long elementInstanceId;

  @JsonProperty("p")
  private long parentElementInstanceId;

  @JsonIgnore
  private String elementId;

  @JsonProperty("e")
  private byte[] elementHash;

  @JsonProperty("c")
  private int passedCnt;

  @JsonIgnore
  public boolean isTerminated() {
    return false;
  }

  @JsonIgnore
  public boolean isFailed() {
    return false;
  }

  @JsonIgnore
  public boolean isWaiting() {
    return false;
  }
}
