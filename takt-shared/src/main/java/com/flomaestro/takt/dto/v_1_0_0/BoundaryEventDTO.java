package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BoundaryEventDTO extends CatchEventDTO {

  @JsonProperty("f")
  private String attachedToRef;

  @JsonProperty("x")
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
