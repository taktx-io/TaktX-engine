package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.EndThrowingEvent;
import nl.qunit.bpmnmeister.pi.FlowElementTrigger;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.Trigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EndEventProcessor extends EventProcessor<EndEvent, EndEventState> {
  private static final Logger LOG = Logger.getLogger(EndEventProcessor.class);

  @Override
  protected TriggerResult triggerEvent(
      FlowElementTrigger trigger,
      ProcessInstance processInstance,
      EndEvent element,
      EndEventState oldState) {
    Set<Trigger> processInstanceTriggers = new HashSet<>();
    if (!processInstance.getParentProcessInstanceKey().equals(ProcessInstanceKey.NONE)) {
      processInstanceTriggers.add(
          new FlowElementTrigger(
              processInstance.getParentProcessInstanceKey(),
              ProcessInstanceKey.NONE,
              processInstance.getParentElementId(),
              ProcessDefinition.NONE,
              Constants.NONE,
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
        Variables.EMPTY);
  }

  @Override
  public EndEventState initialState() {
    return new EndEventState(UUID.randomUUID(), 0);
  }
}
