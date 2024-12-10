package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class FlowNodeDTO extends FlowElementDTO {
  private final Set<String> incoming;
  private final Set<String> outgoing;

  protected FlowNodeDTO(String id, String parentId, Set<String> incoming, Set<String> outgoing) {
    super(id, parentId);
    this.incoming = incoming;
    this.outgoing = outgoing;
  }
}
