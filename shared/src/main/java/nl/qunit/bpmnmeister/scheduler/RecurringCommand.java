package nl.qunit.bpmnmeister.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.cronutils.model.CronType.QUARTZ;

@Builder
@Getter
public class RecurringCommand implements ScheduleCommand {
    private final List<ProcessInstanceTrigger> triggers;
    private final String cron;
    private final String instantiation;

    @JsonCreator
    public RecurringCommand(
            @JsonProperty("triggers") List<ProcessInstanceTrigger> triggers,
            @JsonProperty("cron") String cron,
            @JsonProperty("instantiation") String instantiation) {
        this.triggers = triggers;
        this.cron = cron;
        this.instantiation = instantiation;
    }
    @Override
    public RecurringCommand evaluate(Instant now, Consumer<List<ProcessInstanceTrigger>> triggerConsumer) {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
        CronParser parser = new CronParser(cronDefinition);
        Cron parsedCron = parser.parse(this.cron);
        ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
        Optional<ZonedDateTime> zonedDateTime = executionTime.nextExecution(ZonedDateTime.parse(instantiation));
        if (zonedDateTime.isPresent()) {
            if (now.isAfter(zonedDateTime.get().toInstant())) {
                // Time reached, return triggers
                triggerConsumer.accept(triggers);

                // Return a new command with the next execution time
                return new RecurringCommand(triggers, parsedCron.asString(), zonedDateTime.get().toString());
            } else {
                // Time not yet reached, return this command
                return this;
            }
        }
        // Something went wrong, return null to indicate that this command is done
        return null;
    }
}
