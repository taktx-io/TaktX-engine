package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OneTimeScheduler.class, name = "onetime"),
  @JsonSubTypes.Type(value = FixedRateMessageScheduler.class, name = "fixedrate"),
  @JsonSubTypes.Type(value = RecurringMessageScheduler.class, name = "recurring"),
  // other ScheduleCommand subclasses...
})
public interface MessageScheduler {
  List<SchedulableMessage<?>> getMessages();

  @JsonIgnore
  MessageScheduler evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessage<?>>> triggerConsumer);

  ScheduleType getScheduleType();

  ScheduleKey getScheduleKey();
}
