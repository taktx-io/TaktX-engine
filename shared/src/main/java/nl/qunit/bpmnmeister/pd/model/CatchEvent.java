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
public abstract class CatchEvent<S extends FlowNodeState> extends Event<S> implements WithIoMapping {
  protected final Set<? extends EventDefinition> eventDefinitions;
  private final InputOutputMapping ioMapping;

  protected CatchEvent(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull Set<? extends EventDefinition> eventDefinitions,
      @Nonnull InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.eventDefinitions = eventDefinitions;
    this.ioMapping = ioMapping;
  }

  @JsonIgnore
  public Set<TimerEventDefinition> getTimerEventDefinitions() {
    return eventDefinitions.stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }

  @JsonIgnore
  public Set<MessageEventDefinition> getMessageventDefinitions() {
    return eventDefinitions.stream()
        .filter(MessageEventDefinition.class::isInstance)
        .map(MessageEventDefinition.class::cast)
        .collect(Collectors.toSet());
  }
}
