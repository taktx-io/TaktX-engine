package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;


@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OneTimeCommand.class, name = "onetime"),
        @JsonSubTypes.Type(value = FixedRateCommand.class, name = "fixedrate"),
        @JsonSubTypes.Type(value = RecurringCommand.class, name = "recurring"),
        // other ScheduleCommand subclasses...
})
public interface ScheduleCommand {
  ScheduleCommand evaluate(Instant now, Consumer<List<ProcessInstanceTrigger>> triggerConsumer);

}
