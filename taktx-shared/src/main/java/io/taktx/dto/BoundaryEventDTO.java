package io.taktx.dto;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BoundaryEventDTO extends CatchEventDTO {

  private String attachedToRef;

  private boolean cancelActivity;

  public BoundaryEventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      Set<EventDefinitionDTO> eventDefinitions,
      String attachedToRef,
      boolean cancelActivity,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, eventDefinitions, ioMapping);
    this.attachedToRef = attachedToRef;
    this.cancelActivity = cancelActivity;
  }
}
