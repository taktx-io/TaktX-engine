package nl.qunit.bpmnmeister.engine.pi;

import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceState;

public class BpmnAssert {

  private final ProcessInstance processInstance;

  private BpmnAssert(ProcessInstance processInstance) {
    this.processInstance = processInstance;
  }

  public static ProcessInstanceAssert assertThat(ProcessInstance processInstance) {
    return new ProcessInstanceAssert(processInstance);
  }
}
