package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.ScheduleKeyTypeIdResolver;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeIdResolver(ScheduleKeyTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@EqualsAndHashCode
@Getter
@Setter
@NoArgsConstructor
@ToString
public abstract class ScheduleKeyDTO {

  @JsonProperty("b")
  private TimeBucket timeBucket;

  public ScheduleKeyDTO(TimeBucket timeBucket) {
    this.timeBucket = timeBucket;
  }
}
