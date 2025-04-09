package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IntermediateCatchEventDTO extends CatchEventDTO {

  public IntermediateCatchEventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      Set<EventDefinitionDTO> eventDefinitions,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
  }
}
