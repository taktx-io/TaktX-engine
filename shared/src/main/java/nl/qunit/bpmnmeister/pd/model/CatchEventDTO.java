package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class CatchEventDTO extends EventDTO implements WithIoMappingDTO {
  protected final Set<EventDefinitionDTO> eventDefinitions;

  protected CatchEventDTO(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull Set<EventDefinitionDTO> eventDefinitions,
      @Nonnull InputOutputMappingDTO ioMapping) {
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
