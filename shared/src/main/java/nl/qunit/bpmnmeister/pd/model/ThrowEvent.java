package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class ThrowEvent extends Event {
  protected ThrowEvent(String id, Set<String> incoming, Set<String> outgoing) {
    super(id, incoming, outgoing);
  }
}
