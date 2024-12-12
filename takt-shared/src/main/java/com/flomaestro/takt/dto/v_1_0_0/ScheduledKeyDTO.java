package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public class ScheduledKeyDTO {
  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("st")
  private ScheduleType scheduleType;

  @JsonProperty("ei")
  private String elementId;

  @JsonProperty("tedi")
  private String timerEventDefinitionId;

  public ScheduledKeyDTO(
      ProcessDefinitionKey processDefinitionKey,
      UUID processInstanceKey,
      ScheduleType scheduleType,
      String elementId,
      String timerEventDefinitionId) {
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.scheduleType = scheduleType;
    this.elementId = elementId;
    this.timerEventDefinitionId = timerEventDefinitionId;
  }
}
