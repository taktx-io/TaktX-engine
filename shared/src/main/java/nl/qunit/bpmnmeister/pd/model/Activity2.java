package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class Activity2 extends FlowNode2 implements WithIoMapping {
  private LoopCharacteristics2 loopCharacteristics;
  private InputOutputMapping2 ioMapping;
}
