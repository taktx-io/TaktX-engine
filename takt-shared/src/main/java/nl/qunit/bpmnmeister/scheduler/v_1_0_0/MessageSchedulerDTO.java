package nl.qunit.bpmnmeister.scheduler.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
public interface MessageSchedulerDTO {

  List<SchedulableMessageDTO<?>> getMessages();

  @JsonIgnore
  MessageSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> triggerConsumer);

  ScheduleType getScheduleType();

  ScheduledKeyDTO getScheduledKey();
}
