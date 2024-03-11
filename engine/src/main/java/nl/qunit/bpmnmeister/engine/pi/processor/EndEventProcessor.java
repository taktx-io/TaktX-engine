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
import nl.qunit.bpmnmeister.pi.state.EndEventState;
import nl.qunit.bpmnmeister.pi.state.StateEnum;
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
    if (processInstance.getParentProcessInstanceKey() != null) {
      parentProcessInstanceTriggers.add(
          new ProcessInstanceTrigger(
              processInstance.getParentProcessInstanceKey(),
              ProcessInstanceKey.NULL,
              ProcessDefinition.NULL,
              element.getParentId(),
              false,
              BaseElementId.NULL,
              processInstance.getVariables()));
    }
    return TriggerResult.builder()
        .newElementState(new EndEventState(StateEnum.FINISHED, oldState.getElementInstanceId()))
        .newProcessInstanceTriggers(parentProcessInstanceTriggers)
        .build();
  }

  @Override
  public EndEventState initialState() {
    return new EndEventState(StateEnum.INIT, UUID.randomUUID());
  }

  @Override
  public EndEventState terminate(EndEventState oldState) {
    return new EndEventState(StateEnum.TERMINATED, oldState.getElementInstanceId());
  }
}
