package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public abstract class ThrowEventDTO extends EventDTO {

  @Nonnull private final Set<EventDefinitionDTO> eventDefinitions;

  protected ThrowEventDTO(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull InputOutputMappingDTO ioMapping,
      @Nonnull Set<EventDefinitionDTO> eventDefinitions) {
    super(id, parentId, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }

  @JsonIgnore
  public Set<LinkEventDefinitionDTO> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinitionDTO.class::isInstance)
        .map(LinkEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<TerminateEventDefinitionDTO> getTerminateEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TerminateEventDefinitionDTO.class::isInstance)
        .map(TerminateEventDefinitionDTO.class::cast)
        .collect(Collectors.toSet());
  }
}
