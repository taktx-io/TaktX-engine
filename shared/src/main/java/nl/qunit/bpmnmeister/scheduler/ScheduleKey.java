package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@ToString
@Getter
@EqualsAndHashCode
public class ScheduleKey {
  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final ScheduleType scheduleType;
  private final String elementId;
  private final String timerEventDefinitionId;

  @JsonCreator
  public ScheduleKey(
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("processInstanceKey") UUID processInstanceKey,
      @Nonnull @JsonProperty("scheduleType") ScheduleType scheduleType,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.scheduleType = scheduleType;
    this.elementId = elementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
  }
}
