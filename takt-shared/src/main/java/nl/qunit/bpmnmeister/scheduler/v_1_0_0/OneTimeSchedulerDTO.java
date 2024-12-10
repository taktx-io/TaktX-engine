package nl.qunit.bpmnmeister.scheduler.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

@Getter
@ToString
@EqualsAndHashCode
public class OneTimeSchedulerDTO implements MessageSchedulerDTO {

  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final String targetElementId;
  private final String timerEventDefinitionId;
  private final List<SchedulableMessageDTO<?>> messages;
  private final String when;

  @JsonCreator
  public OneTimeSchedulerDTO(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @JsonProperty("targetElementId") String targetElementId,
      @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId,
      @JsonProperty("messages") List<SchedulableMessageDTO<?>> messages,
      @JsonProperty("when") String when) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.targetElementId = targetElementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
    this.messages = messages;
    this.when = when;
  }

  @Override
  public OneTimeSchedulerDTO evaluate(
      Instant now, BiConsumer<UUID, List<SchedulableMessageDTO<?>>> consumer) {
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
  public ScheduledKeyDTO getScheduledKey() {
    return new ScheduledKeyDTO(
        processDefinitionKey,
        processInstanceKey,
        ScheduleType.ONE_TIME,
        targetElementId,
        timerEventDefinitionId);
  }
}
