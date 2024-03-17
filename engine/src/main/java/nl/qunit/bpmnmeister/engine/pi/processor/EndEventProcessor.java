package nl.qunit.bpmnmeister.engine.pi.processor;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import nl.qunit.bpmnmeister.engine.pi.TriggerResult;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;
import nl.qunit.bpmnmeister.pd.model.EndEvent;
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
    Set<Trigger> parentProcessInstanceTriggers = new HashSet<>();
    if (!processInstance.getParentProcessInstanceKey().equals(ProcessInstanceKey.NONE)) {
      parentProcessInstanceTriggers.add(
          new FlowElementTrigger(
              processInstance.getParentProcessInstanceKey(),
              ProcessInstanceKey.NONE,
              element.getParentId(),
              BaseElementId.NONE,
              processInstance.getVariables()));
    }
    return new TriggerResult(
        new EndEventState(UUID.randomUUID()),
        Set.of(),
        Set.of(),
        parentProcessInstanceTriggers,
        Variables.EMPTY);
  }

  @Override
  public EndEventState initialState() {
    return new EndEventState(UUID.randomUUID());
  }
}
