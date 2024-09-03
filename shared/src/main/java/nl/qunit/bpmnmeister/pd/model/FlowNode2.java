package nl.qunit.bpmnmeister.pd.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowNode2 extends FlowElement2 {
  @Builder.Default private Set<String> incoming = new HashSet<>();
  @Builder.Default private Set<String> outgoing = new HashSet<>();

  public abstract FLowNodeInstance newInstance(FLowNodeInstance parentInstance);
}
