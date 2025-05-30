package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class SubProcessDTO extends ActivityDTO {

  private FlowElementsDTO elements;
  private boolean triggeredByEvent;

  public SubProcessDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      FlowElementsDTO elements,
      InputOutputMappingDTO ioMapping,
      boolean triggeredByEvent) {

    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.elements = elements;
    this.triggeredByEvent = triggeredByEvent;
  }
}
