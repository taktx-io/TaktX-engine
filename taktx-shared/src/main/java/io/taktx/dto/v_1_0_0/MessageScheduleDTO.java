package io.taktx.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.MessageSchedulerTypeIdResolver;
import lombok.Getter;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
@NoArgsConstructor
@Getter
public abstract class MessageScheduleDTO {

  @JsonProperty("msg")
  protected SchedulableMessageDTO message;

  @JsonProperty("it")
  protected long instantiationTime;

  protected MessageScheduleDTO(SchedulableMessageDTO message, long instantiationTime) {
    this.message = message;
    this.instantiationTime = instantiationTime;
  }

  @JsonIgnore
  public abstract Long getNextExecutionTime(long timestamp);
}
