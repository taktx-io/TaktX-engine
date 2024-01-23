package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Builder
@Getter
public class OneTimeCommand implements ScheduleCommand {
    private final List<ProcessInstanceTrigger> triggers;
    private final String when;
    @JsonCreator
    public OneTimeCommand(
            @JsonProperty("triggers") List<ProcessInstanceTrigger> triggers,
            @JsonProperty("when") String when) {
        this.triggers = triggers;
        this.when = when;
    }

    @Override
    public OneTimeCommand evaluate(Instant now, Consumer<List<ProcessInstanceTrigger>> triggerConsumer) {
        if (Instant.parse(when).isBefore(now)) {
            // Time reached, return triggers
            triggerConsumer.accept(triggers);

            // Return null to indicate that this command is done
            return null;
        } else {
            // Time not yet reached, return this command
            return this;
        }
    }

}
