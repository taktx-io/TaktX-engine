package nl.qunit.bpmnmeister.scheduler.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDefinitionKey;

@ToString
@Getter
@Setter
@EqualsAndHashCode
public class ScheduledKeyDTO {
  private final ProcessDefinitionKey processDefinitionKey;
  private final UUID processInstanceKey;
  private final ScheduleType scheduleType;
  private final String elementId;
  private final String timerEventDefinitionId;

  @JsonCreator
  public ScheduledKeyDTO(
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
