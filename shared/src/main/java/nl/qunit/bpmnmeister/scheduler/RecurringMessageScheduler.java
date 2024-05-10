package nl.qunit.bpmnmeister.scheduler;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class RecurringMessageScheduler implements MessageScheduler {
  private final List<SchedulableMessage<?>> messages;
  private final String cron;
  private final String instantiation;

  @JsonCreator
  public RecurringMessageScheduler(
      @JsonProperty("messages") List<SchedulableMessage<?>> messages,
      @JsonProperty("cron") String cron,
      @JsonProperty("instantiation") String instantiation) {
    this.messages = messages;
    this.cron = cron;
    this.instantiation = instantiation;
  }

  @Override
  public RecurringMessageScheduler evaluate(
      Instant now, Consumer<List<SchedulableMessage<?>>> triggerConsumer) {
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
    CronParser parser = new CronParser(cronDefinition);
    Cron parsedCron = parser.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(ZonedDateTime.parse(instantiation));
    if (zonedDateTime.isPresent()) {
      if (now.isAfter(zonedDateTime.get().toInstant())) {
        // Time reached, return triggers
        triggerConsumer.accept(messages);

        // Return a new command with the next execution time
        return new RecurringMessageScheduler(
            messages, parsedCron.asString(), zonedDateTime.get().toString());
      } else {
        // Time not yet reached, return this command
        return this;
      }
    }
    // Something went wrong, return null to indicate that this command is done
    return null;
  }

  @Override
  public ScheduleType getScheduleType() {
    return ScheduleType.RECURRING;
  }
}
