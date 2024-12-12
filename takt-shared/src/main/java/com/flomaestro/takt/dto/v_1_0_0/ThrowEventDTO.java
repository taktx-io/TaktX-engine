package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class ThrowEventDTO extends EventDTO {

  @JsonProperty("edf")
  private Set<EventDefinitionDTO> eventDefinitions;

  protected ThrowEventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      InputOutputMappingDTO ioMapping,
      Set<EventDefinitionDTO> eventDefinitions) {
    super(id, parentId, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }
}
