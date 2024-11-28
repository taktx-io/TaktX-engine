package nl.qunit.bpmnmeister.engine.pi;

import lombok.Getter;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.instances.FLowNodeInstance;

@Getter
public class ProcessInstanceException extends RuntimeException {

  private final ProcessInstance processInstance;
  private final FLowNodeInstance<?> flowNodeInstance;

  public ProcessInstanceException(
      ProcessInstance processInstance, FLowNodeInstance<?> flowNodeInstance, String message) {
    super(message);
    this.processInstance = processInstance;
    this.flowNodeInstance = flowNodeInstance;
  }

  public ProcessInstanceException(
      ProcessInstance processInstance,
      FLowNodeInstance<?> flowNodeInstance,
      String message,
      Throwable throwable) {
    super(message, throwable);
    this.processInstance = processInstance;
    this.flowNodeInstance = flowNodeInstance;
  }
}
