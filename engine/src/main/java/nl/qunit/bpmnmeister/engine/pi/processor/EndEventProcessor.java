package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pi.EndThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EndEventState;

@ApplicationScoped
public class EndEventProcessor extends EventProcessor<EndEvent, EndEventState> {

  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      EndEvent element,
      EndEventState oldState) {
    Set<ProcessInstanceTrigger> processInstanceTriggers = new HashSet<>();
    if (!processInstance.getParentInstanceKey().equals(ProcessInstanceKey.NONE)) {
      processInstanceTriggers.add(
          new FlowElementTrigger(
              processInstance.getParentInstanceKey(),
              processInstance.getParentElementId(),
              Constants.NONE,
              processInstance.getVariables()));
    }
    EndEventState newState =
        new EndEventState(oldState.getElementInstanceId(), oldState.getPassedCnt() + 1);
    return new TriggerResult(
        newState,
        Set.of(),
        Set.of(),
        processInstanceTriggers,
        Set.of(),
        new EndThrowingEvent(),
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }
}
