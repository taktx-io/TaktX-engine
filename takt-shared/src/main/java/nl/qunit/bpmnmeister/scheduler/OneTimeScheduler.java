package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
public class OneTimeScheduler implements MessageScheduler {

  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final String targetElementId;
  private final String timerEventDefinitionId;
  private final List<SchedulableMessage<?>> messages;
  private final String when;

  @JsonCreator
  public OneTimeScheduler(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("targetElementId") String targetElementId,
      @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId,
      @JsonProperty("messages") List<SchedulableMessage<?>> messages,
      @JsonProperty("when") String when) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
    this.messages = messages;
    this.when = when;
  }

  @Override
  public OneTimeScheduler evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessage<?>>> consumer) {
    if (Instant.parse(when).isBefore(now)) {
      // Time reached, return triggers
      consumer.accept(processInstanceKey, messages);

      // Return null to indicate that this command is done
      return null;
    } else {
      // Time not yet reached, return this command
      return this;
    }
  }

  @Override
  public ScheduleType getScheduleType() {
    return ScheduleType.ONE_TIME;
  }

  @Override
  public ScheduledKey getScheduledKey() {
    return new ScheduledKey(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.ONE_TIME,
        targetElementId,
        timerEventDefinitionId);
  }
}
