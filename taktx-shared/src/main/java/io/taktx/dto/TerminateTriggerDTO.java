package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class TerminateTriggerDTO extends ProcessInstanceTriggerDTO {
  private List<Long> elementInstanceIdPath;

  public TerminateTriggerDTO(UUID processInstanceKey, List<Long> elementInstanceIdPath) {
    super(processInstanceKey, VariablesDTO.empty());
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
