package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import nl.qunit.bpmnmeister.pi.StartCommand;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OneTimeStartCommand.class, name = "onetime"),
  @JsonSubTypes.Type(value = FixedRateStartCommand.class, name = "fixedrate"),
  @JsonSubTypes.Type(value = RecurringStartCommand.class, name = "recurring"),
  // other ScheduleCommand subclasses...
})
public interface ScheduleStartCommand {
  List<StartCommand> getStartCommands();

  ScheduleStartCommand evaluate(
      Instant now, Consumer<List<StartCommand>> triggerConsumer);
}
