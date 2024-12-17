package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ContinueFlowElementTriggerDTO extends ProcessInstanceTriggerDTO
    implements SchedulableMessageDTO {

  public static final ContinueFlowElementTriggerDTO NONE =
      new ContinueFlowElementTriggerDTO(
          Constants.NONE_UUID, List.of(), Constants.NONE, VariablesDTO.empty());

  @JsonProperty("ifi")
  private String inputFlowId;

  @JsonProperty("eiip")
  private List<UUID> elementInstanceIdPath;

  public ContinueFlowElementTriggerDTO(
      UUID processInstanceKey,
      List<UUID> elementInstanceIdPath,
      String inputFlowId,
      VariablesDTO variables) {
    super(processInstanceKey, List.of(), variables);
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.inputFlowId = inputFlowId;
  }
}
