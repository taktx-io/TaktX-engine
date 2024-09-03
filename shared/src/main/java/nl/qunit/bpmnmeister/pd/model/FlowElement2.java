package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class FlowElement2 extends BaseElement2 {
  @Setter
  private FlowElement2 parentElement;
}
