package com.flomaestro.engine.pi;

import com.flomaestro.engine.pi.model.FlowNodeInstance;
import com.flomaestro.engine.pi.model.ProcessInstance;
import lombok.Getter;

@Getter
public class ProcessInstanceException extends RuntimeException {

  private final ProcessInstance processInstance;
  private final FlowNodeInstance<?> flowNodeInstance;

  public ProcessInstanceException(
      ProcessInstance processInstance, FlowNodeInstance<?> flowNodeInstance, String message) {
    super(message);
    this.processInstance = processInstance;
    this.flowNodeInstance = flowNodeInstance;
  }

}
