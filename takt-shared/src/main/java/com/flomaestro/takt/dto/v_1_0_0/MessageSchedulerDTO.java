package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.MessageSchedulerTypeIdResolver;
import java.time.Instant;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
public interface MessageSchedulerDTO {

  @JsonIgnore
  MessageSchedulerDTO evaluate(
      Instant now,
      ScheduleKeyDTO scheduleKey,
      BiConsumer<ScheduleKeyDTO, SchedulableMessageDTO> triggerConsumer);
}
