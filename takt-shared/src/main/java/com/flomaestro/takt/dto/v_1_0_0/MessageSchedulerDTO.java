package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.MessageSchedulerTypeIdResolver;
import java.time.Instant;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(MessageSchedulerTypeIdResolver.class)
public interface MessageSchedulerDTO {

  @JsonIgnore
  MessageSchedulerDTO evaluate(
      Instant now, BiConsumer<ScheduleKeyDTO, SchedulableMessageDTO> triggerConsumer);
}
