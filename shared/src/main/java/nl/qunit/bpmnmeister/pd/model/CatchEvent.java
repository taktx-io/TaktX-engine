package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class CatchEvent<S extends FlowNodeState> extends Event<S> {
  protected final Set<EventDefinition> eventDefinitions;

  protected CatchEvent(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull Set<EventDefinition> eventDefinitions) {
    super(id, parentId, incoming, outgoing);
    this.eventDefinitions = eventDefinitions;
  }

  @JsonIgnore
  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

}
