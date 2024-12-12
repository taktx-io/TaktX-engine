package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.CustomTypeIdResolver;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CUSTOM, property = "cls")
@JsonTypeIdResolver(CustomTypeIdResolver.class)
public interface MessageSchedulerDTO {

  List<SchedulableMessageDTO<?>> getMessages();

  @JsonIgnore
  MessageSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> triggerConsumer);

  @JsonIgnore
  ScheduleType getScheduleType();

  @JsonIgnore
  ScheduledKeyDTO getScheduledKey();
}
