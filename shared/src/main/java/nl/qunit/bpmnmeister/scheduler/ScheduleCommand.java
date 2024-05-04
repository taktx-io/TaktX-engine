package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OneTimeStartCommand.class, name = "onetime"),
  @JsonSubTypes.Type(value = FixedRateStartCommand.class, name = "fixedrate"),
  @JsonSubTypes.Type(value = RecurringStartCommand.class, name = "recurring"),
  // other ScheduleCommand subclasses...
})
public interface ScheduleCommand {
  List<SchedulableMessage> getSchedulableMessages();

  ScheduleCommand evaluate(Instant now, Consumer<List<SchedulableMessage>> triggerConsumer);
}
