package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
public abstract class ThrowEvent<S extends FlowNodeState> extends Event<S>  {

  @Nonnull
  private final Set<EventDefinition> eventDefinitions;

  protected ThrowEvent(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull InputOutputMapping ioMapping,
      @Nonnull Set<EventDefinition> eventDefinitions) {
    super(id, parentId, incoming, outgoing, ioMapping);
    this.eventDefinitions = eventDefinitions;
  }

  @JsonIgnore
  public Set<LinkEventDefinition> getLinkventDefinitions() {
    return eventDefinitions.stream()
        .filter(LinkEventDefinition.class::isInstance)
        .map(LinkEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<TerminateEventDefinition> getTerminateEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TerminateEventDefinition.class::isInstance)
        .map(TerminateEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }
}
