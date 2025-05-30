package io.taktx.dto;

import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StartEventDTO extends CatchEventDTO {

  private boolean interrupting;

  public StartEventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      Set<EventDefinitionDTO> eventDefinitions,
      InputOutputMappingDTO ioMapping,
      boolean interrupting) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
    this.interrupting = interrupting;
  }
}
