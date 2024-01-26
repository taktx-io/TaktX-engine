package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@Getter
@EqualsAndHashCode
public class ScheduleKey {
  private final ProcessDefinitionKey processDefinitionKey;
  private final ScheduleType scheduleType;
  private final String elementId;
  private final String timerEventDefinitionId;

  @JsonCreator
  public ScheduleKey(
      @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @JsonProperty("scheduleType") ScheduleType scheduleType,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId) {
    this.processDefinitionKey = processDefinitionKey;
    this.scheduleType = scheduleType;
    this.elementId = elementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
  }

  @Override
  public String toString() {
    return String.format(
        "%s-%s-%s-%s", processDefinitionKey, scheduleType, elementId, timerEventDefinitionId);
  }
}
