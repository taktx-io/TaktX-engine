package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.FlowNode;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Slf4j
@ToString(callSuper = true)
public abstract class StateProcessor<E extends BaseElement, S extends FlowNodeState> {
  public abstract TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinition definition,
      FlowNode<?> element,
      ScopedVars variables);
}
