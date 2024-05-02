package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;

@ToString
@Getter
public class ScheduleKey {
  private final ProcessDefinitionKey processDefinitionKey;
  private final ScheduleType scheduleType;
  private final String elementId;
  private final String timerEventDefinitionId;

  @JsonCreator
  public ScheduleKey(
      @Nonnull @JsonProperty("processDefinitionKey") ProcessDefinitionKey processDefinitionKey,
      @Nonnull @JsonProperty("scheduleType") ScheduleType scheduleType,
      @Nonnull @JsonProperty("elementId") String elementId,
      @Nonnull @JsonProperty("timerEventDefinitionId") String timerEventDefinitionId) {
    this.processDefinitionKey = processDefinitionKey;
    this.scheduleType = scheduleType;
    this.elementId = elementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
  }
}
