package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

public class ScheduleKey {
  private final ProcessDefinitionKey processDefinitionKey;
  private final ScheduleType scheduleType;
  private final BaseElementId elementId;
  private final BaseElementId timerEventDefinitionId;

  @JsonCreator
  public ScheduleKey(
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("scheduleType") ScheduleType scheduleType,
      @Nonnull @JsonProperty("elementId") BaseElementId elementId,
      @Nonnull @JsonProperty("timerEventDefinitionId") BaseElementId timerEventDefinitionId) {
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
