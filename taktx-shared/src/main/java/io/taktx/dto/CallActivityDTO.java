package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class CallActivityDTO extends ActivityDTO {

  private String calledElement;

  private boolean propagateAllParentVariables;

  private boolean propagateAllChildVariables;

  public CallActivityDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      String calledElement,
      boolean propagateAllParentVariables,
      boolean propagateAllChildVariables,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.calledElement = calledElement;
    this.propagateAllParentVariables = propagateAllParentVariables;
    this.propagateAllChildVariables = propagateAllChildVariables;
  }
}
