package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class StartFlowElementTriggerDTO extends ProcessInstanceTriggerDTO
    implements SchedulableMessageDTO {

  @JsonProperty("ifi")
  private String inputFlowId;

  protected StartFlowElementTriggerDTO(
      UUID processInstanceKey,
      List<String> elementIdPath,
      String inputFlowId,
      VariablesDTO variables) {
    super(processInstanceKey, elementIdPath, variables);
    this.inputFlowId = inputFlowId;
  }
}
