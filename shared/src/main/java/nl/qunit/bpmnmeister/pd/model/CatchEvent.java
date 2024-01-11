package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class CatchEvent extends Event {
  protected final Set<EventDefinition> eventDefinitions;

  protected CatchEvent(
      Set<EventDefinition> eventDefinitions,
      String id,
      Set<String> incoming,
      Set<String> outgoing) {
    super(id, incoming, outgoing);
    this.eventDefinitions = eventDefinitions;
  }
}
