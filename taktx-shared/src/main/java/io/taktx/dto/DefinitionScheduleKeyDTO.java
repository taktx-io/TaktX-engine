package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DefinitionScheduleKeyDTO extends ScheduleKeyDTO {

  private ProcessDefinitionKey processDefinitionKey;
  private String flowNodeId;

  public DefinitionScheduleKeyDTO(
      ProcessDefinitionKey processDefinitionKey, String flowNodeId, TimeBucket timeBucket) {
    super(timeBucket);
    this.processDefinitionKey = processDefinitionKey;
    this.flowNodeId = flowNodeId;
  }
}
