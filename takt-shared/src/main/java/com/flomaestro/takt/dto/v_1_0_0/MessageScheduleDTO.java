package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.MessageSchedulerTypeIdResolver;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
@NoArgsConstructor
public abstract class MessageScheduleDTO {

  @JsonProperty("msgs")
  protected SchedulableMessageDTO message;

  public MessageScheduleDTO(SchedulableMessageDTO message) {
    this.message = message;
  }

  @JsonIgnore
  public abstract TimeBucket getTimeBucket(long millis);

  @JsonIgnore
  public abstract Long getNextExecutionTime(long timestamp);
}
