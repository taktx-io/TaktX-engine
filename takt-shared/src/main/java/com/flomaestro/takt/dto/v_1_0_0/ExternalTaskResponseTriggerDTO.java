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
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ExternalTaskResponseTriggerDTO extends ContinueFlowElementTriggerDTO {

  @JsonProperty("ex")
  private ExternalTaskResponseResultDTO externalTaskResponseResult;

  public ExternalTaskResponseTriggerDTO(
      UUID processInstanceKey,
      List<UUID> elementInstanceIdPath,
      ExternalTaskResponseResultDTO externalTaskResponseResult,
      VariablesDTO variables) {
    super(processInstanceKey, elementInstanceIdPath, null, variables);
    this.externalTaskResponseResult = externalTaskResponseResult;
  }
}
