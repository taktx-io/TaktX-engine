package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pi.ProcessInstance;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EndEventProcessor extends EventProcessor<EndEvent, EndEventState> {
  private static final Logger LOG = Logger.getLogger(EndEventProcessor.class);

  @Override
  protected TriggerResult triggerEvent(
      ProcessInstanceTrigger trigger,
      ProcessInstance processInstance,
      EndEvent element,
      EndEventState oldState) {
    Set<ProcessInstanceTrigger> parentProcessInstanceTriggers = Set.of();
    if (!processInstance.getParentProcessInstanceKey().equals(ProcessInstanceKey.NONE)) {
      parentProcessInstanceTriggers.add(
          new ProcessInstanceTrigger(
              processInstance.getParentProcessInstanceKey(),
              ProcessInstanceKey.NONE,
              ProcessDefinition.NONE,
              element.getParentId(),
              false,
              BaseElementId.NONE,
              processInstance.getVariables()));
    }
    return new TriggerResult(
        new EndEventState(UUID.randomUUID()),
        element.getOutgoing(),
        Set.of(),
        Set.of(),
        Variables.EMPTY);
  }

  @Override
  public EndEventState initialState() {
    return new EndEventState(UUID.randomUUID());
  }

  @Override
  public EndEventState terminate(EndEventState oldState) {
    return new EndEventState(oldState.getElementInstanceId());
  }
}
