package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class DefinitionScheduleKeyDTO implements ScheduleKeyDTO {
  @JsonProperty("pdk")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("ei")
  private String elementId;

  public DefinitionScheduleKeyDTO(ProcessDefinitionKey processDefinitionKey, String elementId) {
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }

  @Override
  public String getRecordKey() {
    return processDefinitionKey.getProcessDefinitionId();
  }
}
