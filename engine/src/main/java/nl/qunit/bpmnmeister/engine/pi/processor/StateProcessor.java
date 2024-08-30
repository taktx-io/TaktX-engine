package nl.qunit.bpmnmeister.engine.pi.processor;

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementDTO;
import nl.qunit.bpmnmeister.pd.model.FlowNodeDTO;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionDTO;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateDTO;

@Slf4j
@ToString(callSuper = true)
public abstract class StateProcessor<E extends BaseElementDTO, S extends FlowNodeStateDTO> {
  public abstract TriggerResult trigger(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      ProcessDefinitionDTO definition,
      FlowNodeDTO element,
      ScopedVars variables);
}
