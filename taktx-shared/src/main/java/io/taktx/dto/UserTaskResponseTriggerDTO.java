package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserTaskResponseTriggerDTO extends ContinueFlowElementTriggerDTO {

  private UserTaskResponseResultDTO userTaskResponseResult;

  public UserTaskResponseTriggerDTO(
      UUID processInstanceKey,
      List<Long> elementInstanceIdPath,
      UserTaskResponseResultDTO userTaskResponseResult,
      VariablesDTO variables) {
    super(processInstanceKey, elementInstanceIdPath, null, variables);
    this.userTaskResponseResult = userTaskResponseResult;
  }
}
