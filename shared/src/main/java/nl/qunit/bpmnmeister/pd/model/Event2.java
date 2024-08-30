package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class Event2 extends FlowNode2 implements WithIoMapping {

  private InputOutputMapping2 ioMapping;
}
