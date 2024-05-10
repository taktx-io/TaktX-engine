package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
public abstract class CatchEvent<S extends BpmnElementState> extends Event<S> {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    CatchEvent that = (CatchEvent) o;
    return Objects.equals(eventDefinitions, that.eventDefinitions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), eventDefinitions);
  }
}
