package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public abstract class CatchEventDTO extends EventDTO implements WithIoMappingDTO {
  @JsonProperty("e")
  protected Set<EventDefinitionDTO> eventDefinitions;

  protected CatchEventDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      Set<EventDefinitionDTO> eventDefinitions,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }

  @JsonIgnore
  public Set<TimerEventDefinitionDTO> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinitionDTO.class::isInstance)
        .map(TimerEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<MessageEventDefinitionDTO> getMessageventDefinitions() {
    return eventDefinitions.stream()
        .filter(MessageEventDefinitionDTO.class::isInstance)
        .map(MessageEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<LinkEventDefinitionDTO> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinitionDTO.class::isInstance)
        .map(LinkEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }
}
