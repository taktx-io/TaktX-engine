package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
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
              ProcessDefinition.NONE,
              element.getParentId(),
              BaseElementId.NONE,
              processInstance.getVariables()));
    }
    return new TriggerResult(
        oldState, Set.of(), Set.of(), processInstanceTriggers, Variables.EMPTY);
  }

  @Override
  public EndEventState initialState() {
    return new EndEventState(UUID.randomUUID());
  }
}
