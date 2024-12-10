package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
public interface MessageScheduler {
  List<SchedulableMessage<?>> getMessages();

  @JsonIgnore
  MessageScheduler evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessage<?>>> triggerConsumer);

  ScheduleType getScheduleType();

  ScheduledKey getScheduledKey();
}
