package nl.qunit.bpmnmeister.pd.model;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class FlowNode extends FlowElement {
  private Set<String> incoming;
  private Set<String> outgoing;

  protected FlowNode(String id, Set<String> incoming, Set<String> outgoing) {
    super(id);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
