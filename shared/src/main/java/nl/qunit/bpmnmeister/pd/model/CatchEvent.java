package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public abstract class CatchEvent extends Event {
  protected final Set<EventDefinition> eventDefinitions;

  protected CatchEvent(
      @Nonnull BaseElementId id,
      @Nonnull BaseElementId parentId,
      @Nonnull Set<BaseElementId> incoming,
      @Nonnull Set<BaseElementId> outgoing,
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
