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
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
public class RecurringMessageScheduler implements MessageScheduler {

  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final String targetElementId;
  private final String timerEventDefinitionId;
  private final List<SchedulableMessage<?>> messages;
  private final String cron;
  private final String instantiation;

  @JsonCreator
  public RecurringMessageScheduler(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("targetElementId") String targetElementId,
      @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId,
      @JsonProperty("messages") List<SchedulableMessage<?>> messages,
      @JsonProperty("cron") String cron,
      @JsonProperty("instantiation") String instantiation) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
    this.messages = messages;
    this.cron = cron;
    this.instantiation = instantiation;
  }

  @Override
  public RecurringMessageScheduler evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessage<?>>> triggerConsumer) {
    CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);
    CronParser parser = new CronParser(cronDefinition);
    Cron parsedCron = parser.parse(this.cron);
    ExecutionTime executionTime = ExecutionTime.forCron(parsedCron);
    Optional<ZonedDateTime> zonedDateTime =
        executionTime.nextExecution(ZonedDateTime.parse(instantiation));
    if (zonedDateTime.isPresent()) {
      if (now.isAfter(zonedDateTime.get().toInstant())) {
        // Time reached, return triggers
        triggerConsumer.accept(processInstanceKey, messages);

        // Return a new command with the next execution time
        return new RecurringMessageScheduler(
            processDefinitionKey,
            processInstanceKey,
            targetElementId,
            timerEventDefinitionId,
            messages,
            parsedCron.asString(),
            zonedDateTime.get().toString());
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

  @Override
  public ScheduledKey getScheduledKey() {
    return new ScheduledKey(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.RECURRING,
        targetElementId,
        timerEventDefinitionId);
  }
}
