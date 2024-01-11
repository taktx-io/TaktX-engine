package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class Gateway extends FlowNode {
  protected Gateway(String id, Set<String> incoming, Set<String> outgoing) {
    super(id, incoming, outgoing);
  }
}
