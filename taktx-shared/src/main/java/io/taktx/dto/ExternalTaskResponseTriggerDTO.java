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
public class ExternalTaskResponseTriggerDTO extends ContinueFlowElementTriggerDTO {

  private ExternalTaskResponseResultDTO externalTaskResponseResult;

  public ExternalTaskResponseTriggerDTO(
      UUID processInstanceKey,
      List<Long> elementInstanceIdPath,
      ExternalTaskResponseResultDTO externalTaskResponseResult,
      VariablesDTO variables) {
    super(processInstanceKey, elementInstanceIdPath, null, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
